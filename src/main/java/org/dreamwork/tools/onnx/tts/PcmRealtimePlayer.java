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
    private final long timeout;
    private final Logger logger = LoggerFactory.getLogger (PcmRealtimePlayer.class);
    private final BlockingQueue<SamplesWrapper> queue = new ArrayBlockingQueue<> (1024);
    private final AudioFormat format;

    // 0 - idle
    // 1 - playing
    // 2 - done
    private final AtomicInteger playerStatus = new AtomicInteger (0);

    private volatile Future<?> future;
    private volatile boolean interrupted, running = true;
    private volatile IPlayerListener listener;
    private volatile long timestamp = -1;
    private volatile TTSEventLoop eventLoop;


    public PcmRealtimePlayer (int sampleRate, long timeout) {
        this.timeout = timeout;
        if (logger.isTraceEnabled ()) {
            logger.trace ("pcm realtime player created.");
            logger.trace ("sample rate = {}", sampleRate);
            logger.trace ("timeout = {} ms.", timeout);
        }
        format = new AudioFormat (
                AudioFormat.Encoding.PCM_SIGNED,
                sampleRate,
                16,
                1,            // channels
                2,            // frameSize = 16bit/2byte * 1ch
                sampleRate,   // frameRate
                false         // little-endian
        );
    }

    void setListener (IPlayerListener listener) {
        this.listener = listener;
    }

    void setEventLoop (TTSEventLoop loop) {
        this.eventLoop = loop;
    }

    public AudioFormat getFormat () {
        return format;
    }

    public void start () {

        DataLine.Info info = new DataLine.Info (SourceDataLine.class, format);
        if (!AudioSystem.isLineSupported (info)) {
            throw new IllegalStateException ("SourceDataLine not supported: " + format);
        }

        ExecutorService executor = Executors.newFixedThreadPool (1);
        future = executor.submit (() -> {
            Thread thread = Thread.currentThread ();
            thread.setName ("pcm.realtime.player");
            if (logger.isTraceEnabled ()) {
                logger.trace ("starting the pcm realtime player");
            }
            final int chunk = 4096;
            try (SourceDataLine line = (SourceDataLine) AudioSystem.getLine (info)) {
                line.open (format);
                line.start ();

                running = true;
                while (running) {
                    SamplesWrapper sw;
                    try {
                        sw = queue.poll (100, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException ex) {
                        Thread.currentThread ().interrupt ();
                        continue;
                    }

                    if (sw != null && sw.id != null) {
                        if (sw.trigger != null && sw.trigger == SamplesWrapper.TRIGGER_START) {
                            if (listener != null && eventLoop != null) {
                                TTSEventLoop.InnerRunner runner = new TTSEventLoop.InnerRunner (sw.id, () -> listener.onStart (sw.id));
                                if (!eventLoop.raise (runner)) {
                                    logger.warn ("cannot offer task for instance: {}", sw.id);
                                }
                            }
                        } else if (sw.trigger != null && sw.trigger == SamplesWrapper.TRIGGER_END) {
                            if (listener != null && eventLoop != null) {
                                TTSEventLoop.InnerRunner runner = new TTSEventLoop.InnerRunner (sw.id, () -> listener.onComplete (sw.id));
                                if (!eventLoop.raise (runner)) {
                                    logger.warn ("cannot offer task to instance: {}", sw.id);
                                }
                            }
                        } else if (sw.samples != null && sw.samples.length > 0) {
                            try {
                                byte[] pcm = sw.samples;
                                int off = 0;
                                playerStatus.set (1); // playing
                                while (off < pcm.length && !interrupted) {
                                    int len = Math.min (chunk, pcm.length - off);
                                    len = line.write (pcm, off, len);
                                    off += len;
                                    timestamp = System.currentTimeMillis ();
                                }
                            } catch (Throwable ex) {
                                logger.warn (ex.getMessage ());
                            } finally {
                                playerStatus.set (2); // done
                                if (listener != null && eventLoop != null && interrupted) {
                                    TTSEventLoop.InnerRunner runner = new TTSEventLoop.InnerRunner (sw.id, () -> listener.onInterrupted (sw.id));
                                    if (!eventLoop.raise (runner)) {
                                        logger.warn (
                                                "cannot trigger listener.{}",
                                                interrupted ? "onInterrupted" : "onComplete"
                                        );
                                    }
                                }
                            }

                            continue;
                        }
                    }
                    // check idle
                    long now = System.currentTimeMillis ();
                    if (timestamp > 0 && now - timestamp > timeout) {
                        if (logger.isDebugEnabled ()) {
                            logger.debug ("entering idle state");
                        }
                        if (listener != null && eventLoop != null) {
                            if (logger.isDebugEnabled ()) {
                                logger.debug ("trigger listener's onIdle");
                            }
                            TTSEventLoop.InnerRunner task = new TTSEventLoop.InnerRunner (
                                    "", () -> listener.onIdle ()
                            );
                            if (!eventLoop.raise (task)) {
                                logger.warn ("cannot raise an idle event");
                            }
                        }

                        timestamp = 0;
                    }
                }
                // 等播放缓冲区排空再关
                line.drain ();
                line.stop ();
                logger.info ("the pcm realtime player stopped.");
            } catch (Exception ex) {
                logger.warn (ex.getMessage (), ex);
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
        if (logger.isTraceEnabled ()) {
            logger.trace ("trying to stopping pcm realtime player ...");
        }
        running = false;
        queue.clear ();
        interrupt ();
        if (future != null) {
            future.cancel (true);
        }
    }

    public interface IPlayerListener {
        void onStart (String id);
        void onInterrupted (String id);
        void onComplete (String id);
        void onIdle ();
        default void onError (String id, Throwable ex) {
            Logger logger = LoggerFactory.getLogger (IPlayerListener.class);
            logger.error ("an error occurred when running listener: ");
            logger.error (ex.getMessage (), ex);
        }
    }
}