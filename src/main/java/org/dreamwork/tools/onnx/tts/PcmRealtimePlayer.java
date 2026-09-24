package org.dreamwork.tools.onnx.tts;

import org.dreamwork.tools.onnx.tts.util.SamplesWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class PcmRealtimePlayer {
    private final int sampleRate;
    private final Logger logger = LoggerFactory.getLogger (PcmRealtimePlayer.class);
    private final BlockingQueue<SamplesWrapper> queue = new ArrayBlockingQueue<> (1024);
    private final BlockingQueue<InnerRunner> tasks = new ArrayBlockingQueue<> (64);
    // 0 - idle
    // 1 - playing
    // 2 - done
    private final AtomicInteger playerStatus = new AtomicInteger (0);
    private final Future<?>[] futures = new Future<?>[2];

    private volatile boolean interrupted, running = true;
    private volatile IPlayerListener listener;

    public PcmRealtimePlayer (int sampleRate) {
        this.sampleRate = sampleRate;
    }

    void setListener (IPlayerListener listener) {
        this.listener = listener;
    }

    public void start () {
        AudioFormat fmt = new AudioFormat (
                AudioFormat.Encoding.PCM_SIGNED,
                sampleRate,
                16,
                1,            // channels
                2,            // frameSize = 16bit/2byte * 1ch
                sampleRate,   // frameRate
                false         // little-endian
        );
        DataLine.Info info = new DataLine.Info (SourceDataLine.class, fmt);
        if (!AudioSystem.isLineSupported (info)) {
            throw new IllegalStateException ("SourceDataLine not supported: " + fmt);
        }

        ExecutorService executor = Executors.newFixedThreadPool (2);
        futures[0] = executor.submit (() -> {
            final int chunk = 4096;
            try (SourceDataLine line = (SourceDataLine) AudioSystem.getLine (info)) {
                line.open (fmt);
                line.start ();

                running = true;
                while (running) {
                    SamplesWrapper sw;
                    try {
                        sw = queue.poll (200, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException ex) {
                        Thread.currentThread ().interrupt ();
                        continue;
                    }

                    if (sw != null && sw.id != null && sw.samples != null && sw.samples.length > 0) {
                        try {
                            byte[] pcm = floatToS16LE (sw.samples);
                            int off = 0;
                            playerStatus.set (1); // playing
                            if (listener != null) {
                                InnerRunner runner = new InnerRunner (sw.id, () -> listener.onStart (sw.id));
                                if (!tasks.offer (runner)) {
                                    logger.warn ("cannot offer task for instance: {}", sw.id);
                                }
                            }
                            while (off < pcm.length && !interrupted) {
                                int len = Math.min (chunk, pcm.length - off);
                                len = line.write (pcm, off, len);
                                off += len;
                            }
                        } finally {
                            playerStatus.set (2); // done
                            if (listener != null) {
                                InnerRunner runner = new InnerRunner (sw.id, () -> {
                                    if (interrupted) {
                                        listener.onInterrupted (sw.id);
                                    } else {
                                        listener.onComplete (sw.id);
                                    }
                                });
                                if (!tasks.offer (runner)) {
                                    logger.warn (
                                            "cannot trigger listener.{}",
                                            interrupted ? "onInterrupted" : "onComplete"
                                    );
                                }
                            }
                        }
                    }
                }
                // 等播放缓冲区排空再关
                line.drain ();
                line.stop ();
            } catch (Exception ex) {
                logger.warn (ex.getMessage (), ex);
            }
        });

        futures[1] = executor.submit (() -> {
            while (running) {
                InnerRunner runner;
                try {
                    runner = tasks.poll (200, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread ().interrupt ();
                    continue;
                }

                if (runner != null) {
                    try {
                        runner.runner.run ();
                    } catch (Throwable ex) {
                        logger.warn (ex.getMessage (), ex);
                        if (listener != null) {
                            try {
                                listener.onError (runner.id, ex);
                            } catch (Throwable t) {
                                logger.error (t.getMessage (), t);
                            }
                        }
                    }
                }
            }
        });
        executor.shutdown ();
    }

    public boolean play (SamplesWrapper wrapper) {
        return queue.offer (wrapper);
    }

    public void interrupt () {
        interrupted = true;
    }

    @SuppressWarnings("BusyWait")
    public synchronized void interruptAndPlay (SamplesWrapper wrapper) {
        interrupted = true;
        while (playerStatus.get () == 1) { // 音频播放中，等待
            try {
                Thread.sleep (1);
            } catch (InterruptedException ex) {
                logger.warn (ex.getMessage (), ex);
                Thread.currentThread ().interrupt ();
            }
        }
        queue.clear ();
        interrupted = false;
        play (wrapper);
    }

    public void stop () {
        queue.clear ();
        tasks.clear ();
        interrupt ();
        running = false;
        for (Future<?> future : futures) {
            if (future != null) {
                future.cancel (true);
            }
        }
    }

    private static byte[] floatToS16LE (float[] samples) {
        byte[] out = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) {
            float v = Math.max (-1f, Math.min (1f, samples[i]));
            int s = (int) (v * 32767);
            out[i * 2] = (byte) (s & 0xFF);
            out[i * 2 + 1] = (byte) ((s >> 8) & 0xFF);
        }
        return out;
    }

    interface IPlayerListener {
        void onStart (String id);
        void onInterrupted (String id);
        void onComplete (String id);
        default void onError (String id, Throwable ex) {
            Logger logger = LoggerFactory.getLogger (IPlayerListener.class);
            logger.error ("an error occurred when running listener: ");
            logger.error (ex.getMessage (), ex);
        }
    }

    private static final class InnerRunner {
        final String id;
        final Runnable runner;

        public InnerRunner (String id, Runnable runner) {
            this.id = id;
            this.runner = runner;
        }
    }
}