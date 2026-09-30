package org.dreamwork.tools.onnx.tts;

import com.k2fsa.sherpa.onnx.GeneratedAudio;
import com.k2fsa.sherpa.onnx.OfflineTts;
import org.dreamwork.tools.onnx.tts.util.SamplesWrapper;
import org.dreamwork.tools.onnx.tts.util.WaveStreamAdapter;
import org.dreamwork.util.IDisposable;
import org.dreamwork.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.*;

import static org.dreamwork.tools.onnx.tts.TTSConfig.MODE_FORWARDING;
import static org.dreamwork.tools.onnx.tts.TTSConfig.MODE_REALTIME;

@SuppressWarnings ("unused")
public class TTS implements IDisposable, AutoCloseable {
    /** sherpa-onnx offline tts */
    private volatile OfflineTts tts;
    /** 监听器 */
    private volatile ITTSListener listener;
    /** 实时播放器 */
    private volatile PcmRealtimePlayer player;
    /** TTS 内部的运行状态指示器 */
    private volatile boolean running = true;
    /** 重新采样器 */
    private volatile WaveStreamAdapter adapter;

    /** 追踪内部线程的句柄 */
    private final Future<?>[] futures = new Future[2];
    /** 等待转换的队列 */
    private final BlockingQueue<TTSEntry> queue = new ArrayBlockingQueue<> (1024);
    /** 包裹的配置项 */
    private final TTSConfig config = new TTSConfig ();
    /** 等待转换的队列的map形式 */
    private final Map<String, TTSEntry> map = new ConcurrentHashMap<> ();
    /** 事件线程 */
    private final TTSEventLoop eventLoop;

    private static final Logger logger = LoggerFactory.getLogger (TTS.class);

    public TTS () {
        ExecutorService executor = Executors.newFixedThreadPool (2);

        futures[0] = executor.submit (this::mailLoop);

        eventLoop = new TTSEventLoop ();
        futures[1] = executor.submit (eventLoop::mainLoop);

        executor.shutdown ();

        logger.info ("TTS initialed.");
    }

    /** 返回配置器 */
    public TTSConfig config () {
        return config;
    }

    /**
     * 设置监听器
     * @param listener 监听器
     */
    public void setListener (ITTSListener listener) {
        this.listener = listener;
    }

    /**
     * 请求合成一段文本
     * @param text 需要合成语音的文本内容
     * @return 指示这段文本合成任务的唯一标识。若任务提交失败，返回 null
     */
    public String synthesis (String text) {
        checkState ();

        TTSEntry entry = new TTSEntry (StringUtil.uuid (), TTSEntryType.Text, text);
        return synthesis (entry);
    }

    /**
     * 请求合成一段文本
     * @param text 需要合成语音的文本内容
     * @param voice 合成时使用的声音
     * @return 指示这段文本合成任务的唯一标识。若任务提交失败，返回 null
     */
    public String synthesis (String text, VoiceRole voice) {
        TTSEntry entry = new TTSEntry (StringUtil.uuid (), TTSEntryType.Text, text);
        entry.voice = voice.sid;
        return synthesis (entry);
    }

    /**
     * 请求合成一段文本
     * @param text 需要合成语音的文本内容
     * @param voice 合成时使用的声音
     * @return 指示这段文本合成任务的唯一标识。若任务提交失败，返回 null
     */
    public String synthesis (String text, int voice) {
        TTSEntry entry = new TTSEntry (StringUtil.uuid (), TTSEntryType.Text, text);
        entry.voice = voice;
        return synthesis (entry);
    }

    /**
     * 请求合成一段文本
     * @param text 需要合成语音的文本内容
     * @param voice 合成时使用的声音
     * @param speed 语音合成的速度，默认为 1.0f
     * @return 指示这段文本合成任务的唯一标识。若任务提交失败，返回 null
     */
    public String synthesis (String text, VoiceRole voice, float speed) {
        TTSEntry entry = new TTSEntry (StringUtil.uuid (), TTSEntryType.Text, text);
        entry.voice = voice.sid;
        entry.speed = speed;
        return synthesis (entry);
    }

    /**
     * 请求合成一段文本
     * @param text 需要合成语音的文本内容
     * @param voice 合成时使用的声音
     * @param speed 语音合成的速度，默认为 1.0f
     * @return 指示这段文本合成任务的唯一标识。若任务提交失败，返回 null
     */
    public String synthesis (String text, int voice, float speed) {
        TTSEntry entry = new TTSEntry (StringUtil.uuid (), TTSEntryType.Text, text);
        entry.voice = voice;
        entry.speed = speed;
        return synthesis (entry);
    }

    /**
     * 请求合成一段文本并保存到文件 {@code path}
     * @param text 需要合成语音的文本内容
     * @param voice 合成时使用的声音
     * @param speed 语音合成的速度，默认为 1.0f
     * @param target 语音合成后保存的文件
     * @return 指示这段文本合成任务的唯一标识。若任务提交失败，返回 null
     * @throws IOException 若无法创建目标文件
     */
    public String synthesisAndSave (String text, int voice, float speed, Path target) throws IOException {
        // 检查目标文件
        if (target == null) {
            throw new RuntimeException ("target is null");
        }

        {
            // 检查目标文件的父目录
            Path parent = target.getParent ();
            if (Files.notExists (parent)) {
                Files.createDirectories (parent);
            }
        }

        // 提交合成任务
        TTSEntry entry = new TTSEntry (StringUtil.uuid (), TTSEntryType.Text, text);
        entry.voice  = voice;
        entry.speed  = speed;
        entry.action = TTSEntry.ACT_SAVE;
        entry.path   = target;
        return synthesis (entry);
    }

    /**
     * 使用内置的 pcm 实时播放器播放一段 wave/pcm 音频
     * @param path wave/pcm 文件
     * @return 指示这段文本合成任务的唯一标识。若任务提交失败，返回 null
     */
    public String play (Path path) {
        checkState ();
        TTSEntry entry = new TTSEntry (StringUtil.uuid (), TTSEntryType.WaveFile, path);
        if (queue.offer (entry)) {
            map.put (entry.uuid, entry);
            return entry.uuid;
        }
        return null;
    }

    /**
     * 使用内置的 pcm 实时播放器播放一段 wave/pcm 音频
     * @param in wave/pcm 音频流
     * @return 指示这段文本合成任务的唯一标识。若任务提交失败，返回 null
     */
    public String play (InputStream in) {
        checkState ();
        TTSEntry entry = new TTSEntry (StringUtil.uuid (), TTSEntryType.InputStream, in);
        if (queue.offer (entry)) {
            map.put (entry.uuid, entry);
            return entry.uuid;
        }
        return null;
    }

    /**
     * 关闭 TTS。关闭后，这个 TTS 实例不再能用
     */
    @Override
    public void close () {
        dispose ();
    }

    /**
     * 关闭 TTS。关闭后，这个 TTS 实例不再能用
     */
    @Override
    public void dispose () {
        running = false;
        for (Future<?> future : futures) {
            if (future != null) {
                future.cancel (true);
            }
        }

        if (eventLoop != null) {
            eventLoop.stop ();
        }

        if (tts != null) {
            tts.release ();
        }
        if (player != null) {
            player.stop ();
        }

        queue.clear ();
        map.clear ();
    }

    private void checkState () {
        if (!running) {
            throw new IllegalStateException ("TTS has shutdown");
        }
    }

    private String synthesis (TTSEntry entry) {
        if (queue.offer (entry)) {
            map.put (entry.uuid, entry);
            return entry.uuid;
        }
        return null;
    }

    private void mailLoop () {
        Thread thread = Thread.currentThread ();
        thread.setName ("tts.main.loop");

        while (running && !thread.isInterrupted ()) {
            TTSEntry entry;
            try {
                entry = queue.poll (200, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ex) {
                thread.interrupt ();
                continue;
            }

            if (entry != null) {
                switch (entry.type) {
                    case Text:
                        processText (entry);
                        break;

                    case WaveFile:
                        processWaveFile (entry);
                        break;

                    case InputStream:
                        processInputStream (entry);
                        break;
                }
            }
        }
    }

    /**
     * 实际进行 TTS 语音合成的函数
     * @param entry 等待合成的对象
     */
    private void processText (TTSEntry entry) {
        if (!running) {
            logger.warn ("the tts loop was terminated.");
            return;
        }
        config.check ();

        initialOfflineTTS ();
        initialPcmPlayer ();

        // 尝试发起一个合成开始事件
        raiseEvent (entry, SamplesWrapper.TRIGGER_START);
        // 重新计算合成音色和速率
        int sid = config.sid;
        if (entry.voice != null) {
            sid = entry.voice;
        }
        float speed = config.speed;
        if (entry.speed != null) {
            speed = entry.speed;
        }

        if (entry.action == TTSEntry.ACT_SAVE) {
            synthesisThenSave (entry, sid, speed);
        } else {
            // 调用 sherpa-onnx 进行语音合成
            tts.generateWithCallback ((String) entry.target, sid, speed, voice -> {
                if (logger.isTraceEnabled ()) {
                    logger.trace ("received samples.length: {}", voice.length);
                }

                try {
                    if (voice.length > 0) {
                        // 将 sherpa-onnx 的 float[] 转成 pcm 的 byte[]
                        byte[] pcm = floatToS16LE (voice);
                        playOrForward (entry, pcm);
                    }
                } catch (Throwable ex) {
                    logger.error (ex.getMessage (), ex);
                }
            });
        }
        // 尝试发起一个合成结束事件
        raiseEvent (entry, SamplesWrapper.TRIGGER_END);
    }

    private void processWaveFile (TTSEntry entry) {
        initialPcmPlayer ();
        initialAudioAdapter ();


        raiseEvent (entry, SamplesWrapper.TRIGGER_START);
        Path path = (Path) entry.target;
        try {
            adapter.resample (path.toFile (), buff -> playOrForward (entry, buff));
        } catch (Exception ex) {
            logger.warn (ex.getMessage (), ex);
        }
        raiseEvent (entry, SamplesWrapper.TRIGGER_END);
    }

    private void processInputStream (TTSEntry entry) {
        try (InputStream in = (InputStream) entry.target) {
            InputStream alias = in;
            if (!in.markSupported ()) {
                alias = new BufferedInputStream (in, 8192);
            }
            AudioInputStream ais = AudioSystem.getAudioInputStream (alias);
            AudioFormat format = ais.getFormat ();

            initialPcmPlayer ();
            initialAudioAdapter ();

            adapter.resample (alias, buff -> playOrForward (entry, buff));
        } catch (Exception ex) {
            logger.warn (ex.getMessage (), ex);
        }
    }

    public static byte[] floatToS16LE (float[] samples) {
        byte[] out = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) {
            float v = Math.max (-1f, Math.min (1f, samples[i]));
            int s = (int) (v * 32767);
            out[i * 2] = (byte) (s & 0xFF);
            out[i * 2 + 1] = (byte) ((s >> 8) & 0xFF);
        }
        return out;
    }

    private void raiseEvent (TTSEntry entry, int what) {
        if (listener == null) {
            if (logger.isDebugEnabled ()) {
                logger.debug ("there's no listener. ignore this request.");
            }
            return;
        }

        if ((config.mode & MODE_REALTIME) != 0 || entry.type != TTSEntryType.Text) {
            // 实时播放器模式，其事件应该由播放器发起
            player.play (new SamplesWrapper (entry.uuid, what));
        } else if (listener != null && eventLoop != null) {
            if (logger.isTraceEnabled ()) {
                logger.trace ("trying to raising a {} event.", what == SamplesWrapper.TRIGGER_START ? "start" : "finish");
            }

            Runnable runner = null;
            if (what == SamplesWrapper.TRIGGER_START) {
                runner = () -> listener.started (entry.target);
            } else if (what == SamplesWrapper.TRIGGER_END) {
                runner = () -> listener.finished (entry.target);
            }

            if (runner != null) {
                TTSEventLoop.InnerRunner task = new TTSEventLoop.InnerRunner (entry.uuid, runner);
                if (!eventLoop.raise (task)) {
                    logger.warn ("cannot raise the {} event", what == SamplesWrapper.TRIGGER_START ? "start" : "finish");
                }
            } else if (logger.isDebugEnabled ()) {
                logger.debug ("unknown {} trigger, ignore this request", what);
            }
        }
    }

    private void synthesisThenSave (TTSEntry entry, int sid, float speed) {
        GeneratedAudio audio = tts.generate ((String) entry.target, sid, speed);
        if (audio != null) {
            try {
                audio.save (entry.path.toAbsolutePath ().toString ());
                if (listener != null && eventLoop != null) {
                    TTSEventLoop.InnerRunner task = new TTSEventLoop.InnerRunner (
                            entry.uuid,
                            () -> listener.voiceSaved (entry.target, entry.path)
                    );
                    if (!eventLoop.raise (task)) {
                        logger.warn ("cannot raise a save event");
                    }
                }
            } catch (Exception ex) {
                logger.warn (ex.getMessage (), ex);
                if (listener != null && eventLoop != null) {
                    TTSEventLoop.InnerRunner task = new TTSEventLoop.InnerRunner (
                            entry.uuid,
                            () -> listener.handleException (entry.target, ex)
                    );
                    if (!eventLoop.raise (task)) {
                        logger.warn ("cannot raise an error event");
                    }
                }
            }
        }
    }

    private void initialOfflineTTS () {
        if (tts == null) {
            synchronized (this) {
                if (tts == null) {
                    // sherpa-onnx 尚未初始化，在这里进行初始化
                    try {
                        tts = config.build ();
                    } catch (Throwable ex) {
                        logger.warn (ex.getMessage (), ex);
                        ex.printStackTrace (System.err);
                    }
                    if (logger.isDebugEnabled ()) {
                        int speakers = tts.getNumSpeakers ();
                        logger.debug ("there's {} speakers in model {}", speakers, config.model);
                    }
                }
            }
        }
    }

    private void initialPcmPlayer () {
        if (player == null) {
            synchronized (this) {
                if (player == null) {
                    // 初始化 pcm 实时播放器
                    player = new PcmRealtimePlayer (config ().sampleRate, config.timeout);
                    player.setListener (createCpmRealtimeListener ());
                    player.setEventLoop (eventLoop);
                    player.start ();
                }
            }
        }
    }

    private void initialAudioAdapter () {
        if (adapter == null) {
            synchronized (this) {
                if (adapter == null) {
                    adapter = new WaveStreamAdapter (player.getFormat ());
                }
            }
        }
    }

    private void playOrForward (TTSEntry entry, byte[] pcm) {
        if ((config.mode & MODE_REALTIME) != 0) {
            // 喂给播放器
            player.play (new SamplesWrapper (entry.uuid, pcm));
        }

        if ((config.mode & MODE_FORWARDING) != 0) {
            // 开启了流转发模式
            try {
                // 喂给转发流
                config.output.write (pcm);
                config.output.flush ();
            } catch (IOException ex) {
                logger.warn (ex.getMessage (), ex);
            }
        }
    }

    private PcmRealtimePlayer.IPlayerListener createCpmRealtimeListener () {
        return new PcmRealtimePlayer.IPlayerListener () {
            @Override
            public void onStart (String id) {
                TTSEntry entry = map.get (id);
                if (listener != null) {
                    if (entry != null) {
                        listener.started (entry.target);
                    } else {
                        listener.started (null);
                    }
                }
            }

            @Override
            public void onInterrupted (String id) {
                TTSEntry entry = map.remove (id);
                map.clear ();
                if (listener != null) {
                    if (entry != null) {
                        listener.interrupted (entry.target);
                    } else {
                        listener.interrupted (null);
                    }
                }
            }

            @Override
            public void onComplete (String id) {
                TTSEntry entry = map.remove (id);
                if (listener != null) {
                    if (entry != null) {
                        listener.finished (entry.target);
                    } else {
                        listener.finished (null);
                    }
                }
            }

            @Override
            public void onError (String id, Throwable ex) {
                TTSEntry entry = map.remove (id);
                if (listener != null) {
                    if (entry != null) {
                        listener.handleException (entry.target, ex);
                    } else {
                        listener.handleException (null, ex);
                    }
                }
            }

            @Override
            public void onIdle () {
                if (listener != null) {
                    listener.idle ();
                }
            }
        };
    }

    private static final class TTSEntry implements Serializable {
        final String uuid;
        final transient Object target;
        final TTSEntryType type;

        static final int ACT_SAVE    = 1;
        static final int ACT_FORWARD = 2;

        Integer voice;
        Float speed;
        int action; // 1 - save, 2 - forward
        Path path;

        public TTSEntry (String uuid, TTSEntryType type, Object target) {
            this.uuid   = uuid;
            this.type   = type;
            this.target = target;
        }
    }

    private enum TTSEntryType {
        Text, WaveFile, InputStream
    }
}