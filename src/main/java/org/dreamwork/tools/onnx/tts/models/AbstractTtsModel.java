package org.dreamwork.tools.onnx.tts.models;

public abstract class AbstractTtsModel implements ITtsModel {
    final String root;

    public AbstractTtsModel (String root) {
        this.root = root;
    }
}