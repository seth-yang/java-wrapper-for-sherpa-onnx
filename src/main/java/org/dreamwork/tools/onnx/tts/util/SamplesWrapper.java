package org.dreamwork.tools.onnx.tts.util;

import java.util.Arrays;

public class SamplesWrapper {
    public final String  id;
    public final byte[] samples;
    public final Integer trigger;

    public static final int TRIGGER_START = 0x10;
    public static final int TRIGGER_END   = 0x11;

    public SamplesWrapper (String id, byte[] samples) {
        this.id = id;
        this.samples = samples;
        trigger = null;
    }

    public SamplesWrapper (String id, int trigger) {
        this.id = id;
        this.samples = null;
        this.trigger = trigger;
    }

    @Override
    public String toString () {
        return "SamplesWrapper{" +
                "id='" + id + '\'' +
                ", samples=" + samples +
                ", trigger=" + trigger +
                '}';
    }
}