package org.dreamwork.tools.onnx.tts;

public final class VoiceRole {
    public final int sid;
    public final String lang, name, gender, displayName;

    public VoiceRole (int sid, String lang, String name, String gender, String displayName) {
        this.sid = sid;
        this.lang = lang;
        this.name = name;
        this.gender = gender;
        this.displayName = displayName;
    }
}