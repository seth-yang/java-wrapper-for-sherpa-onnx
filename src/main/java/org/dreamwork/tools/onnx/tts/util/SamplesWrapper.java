package org.dreamwork.tools.onnx.tts.util;

public class SamplesWrapper {
    public final String  id;
    public final float[] samples;

    public SamplesWrapper (String id, float[] samples) {
        this.id = id;
        this.samples = samples;
    }
}
