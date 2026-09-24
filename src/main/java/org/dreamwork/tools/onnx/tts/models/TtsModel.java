package org.dreamwork.tools.onnx.tts.models;

import static org.dreamwork.tools.onnx.tts.models.TtsModelType.*;

public enum TtsModel {
    Coqui (NonStreaming),
    Kitten (NonStreaming),
    Kokoro (NonStreaming),
    Matcha (NonStreaming),
    Piper (NonStreaming),
/*
    Vits (NonStreaming),
    Dpdfnet (Streaming),
    Qtcrn (Streaming)
*/
    ;
    public final TtsModelType type;

    TtsModel (TtsModelType type) {
        this.type = type;
    }
}