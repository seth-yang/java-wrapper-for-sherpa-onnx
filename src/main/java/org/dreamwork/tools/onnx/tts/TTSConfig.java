package org.dreamwork.tools.onnx.tts;

import com.k2fsa.sherpa.onnx.OfflineTts;
import org.dreamwork.tools.onnx.tts.models.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class TTSConfig {
    public static final int MODE_REALTIME = 0x01;
    public static final int MODE_SAVE = 0x02;
    public static final int MODE_FORWARDING = 0x04;

    TtsModel model;
    String root;

    transient int sid, sampleRate = 24000;

    volatile long timeout = 10_000L; // 10s
    volatile String dir;

    volatile Path target;
    volatile OutputStream stream;
    volatile OfflineTts tts;
    AtomicBoolean initialed = new AtomicBoolean (false);

    OutputStream output;

    /**
     * 运行模式.
     * <p>允许的运行模式有：</p>
     * <ul>
     *     <li>0x01 - 实时模式</li>
     *     <li>0x02 - 保存文件</li>
     *     <li>0x04 - 转发模式</li>
     * </ul>
     */
    int mode = 0;

    private final Logger logger = LoggerFactory.getLogger (TTSConfig.class);

    TTSConfig () {}

    public TTSConfig model (TtsModel model) {
        this.model = model;
        return this;
    }

    public TTSConfig modelRoot (String root) {
        this.root = root;
        return this;
    }

    /**
     * 设置音频输出的语音角色.
     * @param role 指定的语音角色
     * @return TTSConfig 实例本身
     * @see VoiceRole
     */
    public TTSConfig voice (VoiceRole role) {
        if (role != null) {
            sid = role.sid;
        }
        return this;
    }

    /**
     * 设置音频输出的语音角色.
     * @param role 指定的语音角色
     * @return TTSConfig 实例本身
     * @see VoiceRole
     */
    public TTSConfig voice (int role) {
        sid = role;
        return this;
    }

    /**
     * 设置进入 Idle 状态的超时时间
     * @param amount 时间
     * @param unit   时间单位
     * @return TTSConfig 实例本身
     */
    public TTSConfig timeout (int amount, TimeUnit unit) {
        if (amount < 0) {
            logger.warn ("wrong time amount: {} of {}, use the default value: 30s.", amount, unit);
            timeout = 500L;
        } else {
            timeout = unit.toMillis (amount);
        }
        return this;
    }

    /**
     * 激活文件保存模式
     * @return TTSConfig 实例本身
     */
    public TTSConfig enableSaveMode () {
        mode |= MODE_SAVE;
        return this;
    }

    /**
     * 取消文件保存模式
     * @return TTSConfig 实例本身
     */
    public TTSConfig disableSaveMode () {
        mode &= ~MODE_SAVE;
        return this;
    }

    /**
     * 激活实时模式 (默认行为)
     * @return TTSConfig 实例本身
     */
    public TTSConfig enableRealtimeMode () {
        mode |= MODE_REALTIME;
        return this;
    }

    /**
     * 取消实施模式
     * @return TTSConfig 实例本身
     */
    public TTSConfig disableRealtimeMode () {
        mode &= ~MODE_REALTIME;
        return this;
    }

    /**
     * 激活数据转发模式
     * @return TTSConfig 实例本身
     */
    public TTSConfig enableForwardMode () {
        mode |= MODE_FORWARDING;
        return this;
    }

    /**
     * 取消数据转发模式
     * @return TTSConfig 实例本身
     */
    public TTSConfig disableForwardMode () {
        mode &= ~MODE_FORWARDING;
        return this;
    }

    /**
     * 设置数据转发的出口
     * @param output 数据转发出口
     * @return TTSConfig 实例本身
     */
    public TTSConfig forward (OutputStream output) {
        this.output = output;
        return this;
    }

    /**
     * 设置保存语音转换结果文件的输出目录.
     * <p>如果文件保存模式未被激活，这个设置不会产生任何结果</p>
     * 若指定的 {@code dir} 目录不存，将会尝试创建这个目录。
     * <p><strong>仅在首次调用转换任务前调用有效</strong></p>
     * 您可以使用 '{@code ~}' 来代表操作系统的用户目录
     * @param dir 输出目录
     * @return TTSConfig 实例本身
     */
    public TTSConfig outputDir (String dir) {
        if (dir.startsWith ("~/")) {
            String home = System.getProperty ("user.home");
            dir = home + dir.substring (1);
        }
        Path path = Paths.get (dir);
        if (Files.notExists (path)) {
            try {
                Files.createDirectories (path);
            } catch (IOException ex) {
                throw new RuntimeException (ex);
            }
        }
        if (!Files.isWritable (path)) {
            throw new RuntimeException ("dir " + dir + " cannot be written.");
        }
        this.dir = dir;
        return this;
    }

    public TTSConfig sampleRate (int sampleRate) {
        this.sampleRate = sampleRate;
        return this;
    }

    void check () {
        if ((mode & MODE_SAVE) != 0) {
            if (dir == null || dir.trim ().isEmpty ()) {
                throw new RuntimeException ("you set the TTS in SAVE mode, but not set the output dir.");
            }
/*
            try {
                String format = this.format.toString ();
                int position = format.lastIndexOf ('_');
                String ext = format.substring (position + 1);
                String fileName = sdf.format (System.currentTimeMillis ()) + "." + ext;
                target = Paths.get (dir, fileName);
                stream = Files.newOutputStream (target);
            } catch (IOException ex) {
                logger.warn (ex.getMessage (), ex);
            }
*/
        }
        if ((mode & MODE_FORWARDING) != 0 && output == null) {
            throw new RuntimeException ("you set the TTS in FORWARDING mode, but the output stream is not set.");
        }
    }

    void closeStream () {
        if (stream != null) {
            try {
                stream.flush ();
                stream.close ();
            } catch (IOException ignore) {}
            finally {
                stream = null;
            }
        }
    }

    OfflineTts build () {
        if (initialed.compareAndSet (false, true)) {
            if (model != null) {
                switch (model) {
                    case Coqui:
                        tts = new CoquiModel (root).createTTS ();
                        break;

                    case Kitten:
                        tts = new KittenModel (root).createTTS ();
                        break;

                    case Kokoro:
                        tts = new KokoroModel (root).createTTS ();
                        break;

                    case Matcha:
                        tts = new MatchaModel (root).createTTS ();
                        break;

                    case Piper:
                        tts = new PiperModel (root).createTTS ();
                        break;
                }
            }
        }
        return tts;
    }
}