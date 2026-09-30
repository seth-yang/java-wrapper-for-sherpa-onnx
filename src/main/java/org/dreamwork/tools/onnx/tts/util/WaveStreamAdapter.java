package org.dreamwork.tools.onnx.tts.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;

/**
 * 流式 WAV 格式适配器
 * 将任意 WAV 文件流式转换为目标 AudioFormat，通过 Consumer 回调输出 PCM chunk
 */
public class WaveStreamAdapter {
    private final Logger logger = LoggerFactory.getLogger (WaveStreamAdapter.class);
    private final AudioFormat targetFmt;

    public WaveStreamAdapter (AudioFormat targetFmt) {
        this.targetFmt = targetFmt;
    }

    /**
     * 流式转换入口
     *
     * @param wavFile  源 WAV 文件
     * @param consumer 转换后的 PCM 数据消费者（16bit signed little-endian, 目标采样率/声道）
     */
    public void resample (File wavFile, Consumer<byte[]> consumer) throws Exception {
        if (consumer == null) {
            throw new IllegalArgumentException ("consumer 不能为 null");
        }

        try (AudioInputStream ais = AudioSystem.getAudioInputStream (wavFile)) {
            resample (ais, consumer);
        }
    }

    public void resample (InputStream in, Consumer<byte[]> consumer) throws Exception {
        if (consumer == null) {
            throw new IllegalArgumentException ("consumer 不能为 null");
        }

        if (!in.markSupported ()) {
            in = new BufferedInputStream (in, 8192);
        }
        try (AudioInputStream ais = AudioSystem.getAudioInputStream (in)) {
            resample (ais, consumer);
        }
    }

    private void resample (AudioInputStream ais, Consumer<byte[]> consumer) throws IOException {
        AudioInputStream pcmAis = null;
        try {
            AudioFormat srcFmt = ais.getFormat ();

            if (isFormatCompatible (srcFmt, targetFmt)) {
                streamDirect (ais, consumer);
                return;
            }

            pcmAis = toPcm16 (ais, srcFmt);
            AudioFormat pcmFmt = pcmAis.getFormat ();

            StreamResampler resampler = new StreamResampler (
                    (int) pcmFmt.getSampleRate (),
                    (int) targetFmt.getSampleRate ()
            );

            byte[] readBuf = new byte[4096];
            int readLen;

            while ((readLen = pcmAis.read (readBuf)) != -1) {
                if (readLen == 0) continue;

                float[] samples = bytesToFloat (readBuf, readLen, pcmFmt);

                if (pcmFmt.getChannels () == 2) {
                    samples = stereoToMono (samples);
                }

                float[] resampled = resampler.process (samples);
                if (resampled.length == 0) continue;

                byte[] output = floatToS16LE (resampled);
                consumer.accept (output);
            }

            float[] flushed = resampler.flush ();
            if (flushed.length > 0) {
                byte[] output = floatToS16LE (flushed);
                consumer.accept (output);
            }
        } finally {
            if (pcmAis != null) {
                try {
                    pcmAis.close ();
                } catch (IOException ignored) {}
            }
        }
    }

    // ============ 格式匹配 ============

    private boolean isFormatCompatible (AudioFormat src, AudioFormat dst) {
        boolean compatible =  src.getEncoding ().equals (dst.getEncoding ())
                && src.getSampleRate () == dst.getSampleRate ()
                && src.getSampleSizeInBits () == dst.getSampleSizeInBits ()
                && src.getChannels () == dst.getChannels ()
                && src.getFrameSize () == dst.getFrameSize ()
                && src.isBigEndian () == dst.isBigEndian ();
        if (logger.isTraceEnabled ()) {
            prettyPrintFormat (src, dst);
        }
        return compatible;
    }

    // ============ 直接透传 ============

    private void streamDirect (AudioInputStream ais, Consumer<byte[]> consumer) throws IOException {
        byte[] buf = new byte[4096];
        int len;
        while ((len = ais.read (buf)) != -1) {
            byte[] chunk = new byte[len];
            System.arraycopy (buf, 0, chunk, 0, len);
            consumer.accept (chunk);
        }
    }

    // ============ PCM 格式统一 ============

    private AudioInputStream toPcm16 (AudioInputStream ais, AudioFormat srcFmt) {
        AudioFormat pcm16 = new AudioFormat (
                AudioFormat.Encoding.PCM_SIGNED,
                srcFmt.getSampleRate (), 16, srcFmt.getChannels (),
                srcFmt.getChannels () * 2, srcFmt.getSampleRate (), false
        );

        if (srcFmt.getEncoding ().equals (AudioFormat.Encoding.PCM_SIGNED)
                && srcFmt.getSampleSizeInBits () == 16
                && !srcFmt.isBigEndian ()) {
            return ais;
        }

        try {
            return AudioSystem.getAudioInputStream (pcm16, ais);
        } catch (IllegalArgumentException e) { /* fallback */ }

        if (srcFmt.getEncoding ().equals (AudioFormat.Encoding.PCM_UNSIGNED)
                && srcFmt.getSampleSizeInBits () == 8) {
            return convert8BitUnsignedTo16BitSigned (ais, srcFmt);
        }

        if (srcFmt.getEncoding ().equals (AudioFormat.Encoding.PCM_SIGNED)
                && srcFmt.getSampleSizeInBits () == 16
                && srcFmt.isBigEndian ()) {
            return convertBigEndianToLittleEndian (ais, srcFmt);
        }

        if (srcFmt.getEncoding ().equals (AudioFormat.Encoding.PCM_SIGNED)
                && srcFmt.getSampleSizeInBits () == 24) {
            return convert24BitTo16Bit (ais, srcFmt);
        }

        throw new UnsupportedOperationException (
                "不支持的音频格式，无法转为 16bit PCM: " + srcFmt +
                        "\n建议：用 FFmpeg 预处理为 16bit/" + (int) targetFmt.getSampleRate () + "Hz/mono WAV"
        );
    }

    // ============ 手动格式转换 ============

    private AudioInputStream convert8BitUnsignedTo16BitSigned (
            AudioInputStream ais, AudioFormat srcFmt) {

        AudioFormat dstFmt = new AudioFormat (
                AudioFormat.Encoding.PCM_SIGNED,
                srcFmt.getSampleRate (), 16, srcFmt.getChannels (),
                srcFmt.getChannels () * 2, srcFmt.getSampleRate (), false
        );

        return new AudioInputStream (
                new ConvertingInputStream (ais, srcFmt, dstFmt) {
                    @Override
                    protected int convert (byte[] src, int srcOff, int srcLen, byte[] dst, int dstOff) {
                        int bytesPerFrame = srcFmt.getChannels ();
                        int frames = srcLen / bytesPerFrame;
                        for (int i = 0; i < frames; i++) {
                            for (int ch = 0; ch < srcFmt.getChannels (); ch++) {
                                int unsigned = src[srcOff + i * bytesPerFrame + ch] & 0xFF;
                                int signed = (unsigned - 128) * 256;
                                signed = Math.max (-32768, Math.min (32767, signed));
                                int outIdx = dstOff + (i * srcFmt.getChannels () + ch) * 2;
                                dst[outIdx] = (byte) (signed & 0xFF);
                                dst[outIdx + 1] = (byte) ((signed >> 8) & 0xFF);
                            }
                        }
                        return frames * srcFmt.getChannels () * 2;
                    }
                },
                dstFmt,
                ais.getFrameLength () * 2
        );
    }

    private AudioInputStream convertBigEndianToLittleEndian (
            AudioInputStream ais, AudioFormat srcFmt) {

        AudioFormat dstFmt = new AudioFormat (
                AudioFormat.Encoding.PCM_SIGNED,
                srcFmt.getSampleRate (), 16, srcFmt.getChannels (),
                srcFmt.getChannels () * 2, srcFmt.getSampleRate (), false
        );

        return new AudioInputStream (
                new ConvertingInputStream (ais, srcFmt, dstFmt) {
                    @Override
                    protected int convert (byte[] src, int srcOff, int srcLen, byte[] dst, int dstOff) {
                        int samples = srcLen / 2;
                        for (int i = 0; i < samples; i++) {
                            byte hi = src[srcOff + i * 2];
                            byte lo = src[srcOff + i * 2 + 1];
                            dst[dstOff + i * 2] = lo;
                            dst[dstOff + i * 2 + 1] = hi;
                        }
                        return srcLen;
                    }
                },
                dstFmt,
                ais.getFrameLength ()
        );
    }

    private AudioInputStream convert24BitTo16Bit (
            AudioInputStream ais, AudioFormat srcFmt) {

        AudioFormat dstFmt = new AudioFormat (
                AudioFormat.Encoding.PCM_SIGNED,
                srcFmt.getSampleRate (), 16, srcFmt.getChannels (),
                srcFmt.getChannels () * 2, srcFmt.getSampleRate (), false
        );

        return new AudioInputStream (
                new ConvertingInputStream (ais, srcFmt, dstFmt) {
                    @Override
                    protected int convert (byte[] src, int srcOff, int srcLen, byte[] dst, int dstOff) {
                        int frameCount = srcLen / (3 * srcFmt.getChannels ());
                        for (int f = 0; f < frameCount; f++) {
                            for (int ch = 0; ch < srcFmt.getChannels (); ch++) {
                                int base = srcOff + (f * srcFmt.getChannels () + ch) * 3;
                                int sample24 = (src[base] & 0xFF)
                                        | ((src[base + 1] & 0xFF) << 8)
                                        | ((src[base + 2] & 0xFF) << 16);
                                if ((sample24 & 0x800000) != 0) sample24 |= 0xFF000000;
                                int sample16 = sample24 >> 8;
                                sample16 = Math.max (-32768, Math.min (32767, sample16));
                                int outBase = dstOff + (f * srcFmt.getChannels () + ch) * 2;
                                dst[outBase] = (byte) (sample16 & 0xFF);
                                dst[outBase + 1] = (byte) ((sample16 >> 8) & 0xFF);
                            }
                        }
                        return frameCount * srcFmt.getChannels () * 2;
                    }
                },
                dstFmt,
                ais.getFrameLength () * 2 / 3
        );
    }

    // ============ 抽象转换流基类 ============

    private abstract static class ConvertingInputStream extends InputStream {
        protected final AudioInputStream source;
        protected final AudioFormat srcFmt;
        protected final AudioFormat dstFmt;
        protected final byte[] readBuf;
        protected byte[] pending = new byte[0];
        protected int pendingOff;
        protected int pendingLen;
        private boolean sourceExhausted;

        protected ConvertingInputStream (AudioInputStream source, AudioFormat srcFmt, AudioFormat dstFmt) {
            this.source = source;
            this.srcFmt = srcFmt;
            this.dstFmt = dstFmt;
            this.readBuf = new byte[4096];
        }

        protected abstract int convert (byte[] src, int srcOff, int srcLen, byte[] dst, int dstOff);

        @Override
        public int read () throws IOException {
            byte[] b = new byte[1];
            int n = read (b, 0, 1);
            if (n == -1) return -1;
            return b[0] & 0xFF;
        }

        @Override
        public int read (byte[] b, int off, int len) throws IOException {
            if (len == 0) return 0;

            int filled = 0;

            // 1. 先消费 pending
            if (pendingLen > 0) {
                int toCopy = Math.min (pendingLen, len);
                System.arraycopy (pending, pendingOff, b, off, toCopy);
                filled += toCopy;
                pendingOff += toCopy;
                pendingLen -= toCopy;
                if (pendingLen == 0) {
                    pendingOff = 0;
                    pending = new byte[0];
                }
                if (filled == len) return filled;
            }

            // 2. 从源读并转换
            while (filled < len && !sourceExhausted) {
                int srcRead = source.read (readBuf);
                if (srcRead == -1) {
                    sourceExhausted = true;
                    break;
                }

                int frameSize = srcFmt.getFrameSize ();
                if (frameSize > 0 && srcRead % frameSize != 0) {
                    srcRead = (srcRead / frameSize) * frameSize;
                }
                if (srcRead == 0) continue;

                byte[] converted = new byte[srcRead * 2 + 4096];
                int convertedLen = convert (readBuf, 0, srcRead, converted, 0);
                if (convertedLen == 0) continue;

                int needed = len - filled;
                if (convertedLen > needed) {
                    System.arraycopy (converted, 0, b, off + filled, needed);
                    filled += needed;
                    pending = new byte[convertedLen - needed];
                    System.arraycopy (converted, needed, pending, 0, convertedLen - needed);
                    pendingOff = 0;
                    pendingLen = convertedLen - needed;
                } else {
                    System.arraycopy (converted, 0, b, off + filled, convertedLen);
                    filled += convertedLen;
                }
            }

            if (filled == 0 && sourceExhausted) return -1;
            return filled;
        }

        @Override
        public void close () throws IOException {
            source.close ();
        }
    }

    // ============ 工具方法 ============

    private float[] bytesToFloat (byte[] buf, int len, AudioFormat fmt) {
        int bytesPerSample = fmt.getSampleSizeInBits () / 8;
        int sampleCount = len / bytesPerSample;
        float[] out = new float[sampleCount];

        if (bytesPerSample == 2) {
            for (int i = 0; i < sampleCount; i++) {
                int val = (buf[i * 2] & 0xFF) | (buf[i * 2 + 1] << 8);
                out[i] = val / 32768.0f;
            }
        } else if (bytesPerSample == 3) {
            for (int i = 0; i < sampleCount; i++) {
                int base = i * 3;
                int val = (buf[base] & 0xFF) | ((buf[base + 1] & 0xFF) << 8) | ((buf[base + 2] & 0xFF) << 16);
                if ((val & 0x800000) != 0) val |= 0xFF000000;
                out[i] = val / 8388608.0f;
            }
        }
        return out;
    }

    private float[] stereoToMono (float[] interleaved) {
        int frames = interleaved.length / 2;
        float[] mono = new float[frames];
        for (int i = 0; i < frames; i++) {
            mono[i] = (interleaved[i * 2] + interleaved[i * 2 + 1]) * 0.5f;
        }
        return mono;
    }

    private byte[] floatToS16LE (float[] samples) {
        byte[] out = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) {
            int val = (int) (samples[i] * 32767);
            val = Math.max (-32768, Math.min (32767, val));
            out[i * 2] = (byte) (val & 0xFF);
            out[i * 2 + 1] = (byte) ((val >> 8) & 0xFF);
        }
        return out;
    }

    private void prettyPrintFormat (AudioFormat src, AudioFormat dst) {
        String[][] output = new String[7][3];
        // <null> src format | dst format
        // 0 - src, 1 - dst
        // .0 - encoding, .1 - sample rate, .2 - sample size
        // .3 - channels, .4 - frame size, .5 - big endian
        output[0][0] = ""; output[0][1] = "src format"; output[0][2] = "dst format";

        output[1][0] = "encoding";
        output[1][1] = src.getEncoding ().toString ();
        output[1][2] = dst.getEncoding ().toString ();

        output[2][0] = "sample rate";
        output[2][1] = String.format ("%.2f", src.getSampleRate ());
        output[2][2] = String.format ("%.2f", dst.getSampleRate ());

        output[3][0] = "sample size";
        output[3][1] = String.valueOf (src.getSampleSizeInBits ());
        output[3][2] = String.valueOf (dst.getSampleSizeInBits ());

        output[4][0] = "channels";
        output[4][1] = String.valueOf (src.getChannels ());
        output[4][2] = String.valueOf (dst.getChannels ());

        output[5][0] = "frame size";
        output[5][1] = String.valueOf (src.getFrameSize ());
        output[5][2] = String.valueOf (dst.getFrameSize ());

        output[6][0] = "big endian";
        output[6][1] = String.valueOf (src.isBigEndian ());
        output[6][2] = String.valueOf (dst.isBigEndian ());
        int lineLength = -1;
        for (String[] line : output) {
            String text = String.format (
                    "%-12s| %-12s | %-12s",
                    line[0], line[1], line[2]
            );
            logger.trace (text);
            if (lineLength < 0) {
                lineLength = text.length ();
                StringBuilder builder = new StringBuilder ();
                for (int i = 0; i < lineLength; i ++) {
                    builder.append ("-");
                }
                logger.trace (builder.toString ());
            }
        }
    }

    // ============ 带状态流式重采样器 ============

    static class StreamResampler {
        private final double ratio;
        private float[] leftover = new float[0];
        private double srcIdx = 0;

        StreamResampler (int srcRate, int dstRate) {
            this.ratio = (double) srcRate / dstRate;
        }

        float[] process (float[] input) {
            if (ratio == 1.0) return input;

            float[] combined = concat (leftover, input);
            leftover = new float[0];

            int outputLen = (int) ((combined.length - srcIdx) / ratio) + 1;
            if (outputLen <= 0) {
                leftover = combined;
                return new float[0];
            }

            float[] output = new float[outputLen];
            int outPos = 0;

            while (outPos < outputLen) {
                int idx = (int) srcIdx;
                double frac = srcIdx - idx;

                if (idx + 1 < combined.length) {
                    output[outPos] = combined[idx] * (float) (1 - frac)
                            + combined[idx + 1] * (float) frac;
                } else if (idx < combined.length) {
                    output[outPos] = combined[idx];
                } else {
                    leftover = subArray (combined, idx);
                    srcIdx = 0;
                    break;
                }

                outPos++;
                srcIdx += ratio;
            }

            if (srcIdx >= combined.length) {
                int idx = (int) srcIdx;
                if (idx < combined.length) {
                    leftover = subArray (combined, idx);
                } else {
                    leftover = new float[0];
                }
                srcIdx = 0;
            }

            return output;
        }

        float[] flush () {
            if (leftover.length == 0) return new float[0];

            float[] padded = new float[leftover.length + 1];
            System.arraycopy (leftover, 0, padded, 0, leftover.length);
            padded[leftover.length] = 0;

            float[] output = new float[(int) (padded.length / ratio) + 1];
            int outPos = 0;

            while (true) {
                int idx = (int) srcIdx;
                double frac = srcIdx - idx;

                if (idx + 1 < padded.length) {
                    output[outPos] = padded[idx] * (float) (1 - frac)
                            + padded[idx + 1] * (float) frac;
                    outPos++;
                    srcIdx += ratio;
                } else {
                    break;
                }
            }

            leftover = new float[0];
            srcIdx = 0;

            float[] result = new float[outPos];
            System.arraycopy (output, 0, result, 0, outPos);
            return result;
        }

        private static float[] concat (float[] a, float[] b) {
            float[] c = new float[a.length + b.length];
            System.arraycopy (a, 0, c, 0, a.length);
            System.arraycopy (b, 0, c, a.length, b.length);
            return c;
        }

        private static float[] subArray (float[] src, int from) {
            float[] result = new float[src.length - from];
            System.arraycopy (src, from, result, 0, result.length);
            return result;
        }
    }
}