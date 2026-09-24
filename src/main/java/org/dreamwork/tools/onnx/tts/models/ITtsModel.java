package org.dreamwork.tools.onnx.tts.models;

import com.k2fsa.sherpa.onnx.OfflineTts;
import org.dreamwork.tools.onnx.tts.VoiceRole;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public interface ITtsModel {
    Collection<VoiceRole> getAvailableVoices ();

    OfflineTts createTTS ();

    default List<VoiceRole> byGender (String gender) {
        Collection<VoiceRole> c = getAvailableVoices ();
        if (c == null || c.isEmpty ()) {
            return Collections.emptyList ();
        }
        List<VoiceRole> roles = new ArrayList<> ();
        c.stream ().filter (r -> r.gender.equals (gender)).forEach (roles::add);
        return Collections.unmodifiableList (roles);
    }

    default List<VoiceRole> byLang (String lang) {
        Collection<VoiceRole> c = getAvailableVoices ();
        if (c == null || c.isEmpty ()) {
            return Collections.emptyList ();
        }
        List<VoiceRole> roles = new ArrayList<> ();
        c.stream ().filter (r -> r.lang.equals (lang)).forEach (roles::add);
        return Collections.unmodifiableList (roles);
    }

    default List<VoiceRole> byLangAndGender (String lang, String gender) {
        List<VoiceRole> roles = new ArrayList<> ();
        byLang (lang).stream().filter (r -> r.gender.equals (gender)).forEach (roles::add);
        return Collections.unmodifiableList (roles);
    }
}
