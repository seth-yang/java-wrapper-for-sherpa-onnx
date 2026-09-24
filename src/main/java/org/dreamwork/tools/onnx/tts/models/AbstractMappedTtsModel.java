package org.dreamwork.tools.onnx.tts.models;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.k2fsa.sherpa.onnx.OfflineTts;
import org.dreamwork.tools.onnx.tts.VoiceRole;
import org.dreamwork.tools.onnx.tts.util.JsonHelper;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractMappedTtsModel extends AbstractTtsModel {
    private final AtomicBoolean created = new AtomicBoolean (false);

    protected static final List<VoiceRole> availableVoices = new ArrayList<> ();
    protected volatile OfflineTts tts;

    public AbstractMappedTtsModel (String root) {
        super (root);
        loadVoices ();
    }

    @Override
    public Collection<VoiceRole> getAvailableVoices () {
        return availableVoices;
    }

    @Override
    public OfflineTts createTTS () {
        if (created.compareAndSet (false, true)) {
            tts = generateTTS ();
        }
        return tts;
    }

    protected synchronized void loadVoices () {
        if (availableVoices.isEmpty ()) {
            try (InputStream in = getMappingResource ()) {
                if (in != null) {
                    TypeReference<List<Map<String, Object>>> type = new TypeReference<List<Map<String, Object>>> () {};
                    ObjectMapper jackson = JsonHelper.jackson;
                    List<Map<String, Object>> maps = jackson.readValue (in, type);
                    if (maps != null && !maps.isEmpty ()) {
                        for (Map<String, Object> map : maps) {
                            int sid = (int) map.get ("sid");
                            String lang = (String) map.get ("lang");
                            String gender = (String) map.get ("gender");
                            String name = (String) map.get ("name");
                            String displayName = (String) map.get ("displayName");
                            availableVoices.add (new VoiceRole (sid, lang, name, gender, displayName));
                        }
                    }
                } else {
                    throw new RuntimeException ("cannot load kokoro voices");
                }
            } catch (IOException ex) {
                throw new RuntimeException ("cannot load kokoro voices");
            }
        }
    }

    protected abstract InputStream getMappingResource ();
    protected abstract OfflineTts generateTTS ();
}