package org.dreamwork.tools.onnx.tts.models;

import com.k2fsa.sherpa.onnx.OfflineTts;
import org.dreamwork.tools.onnx.tts.VoiceRole;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
                    BufferedReader reader = new BufferedReader (
                            new InputStreamReader (in, StandardCharsets.UTF_8)
                    );
                    String line;
                    while ((line = reader.readLine ()) != null) {
                        line = line.trim ();
                        if (line.startsWith ("{") && line.endsWith ("},")) {
                            String[] temp = line.substring (1, line.length () - 3).split (",");

                            int sid = 0;
                            String lang = null, gender = null, name = null, displayName = null;
                            for (String p : temp) {
                                String[] arr = p.trim ().split (":");
                                if (arr.length >= 2) {
                                    String key = arr[0].trim (), value = arr[1].trim ();
                                    switch (key) {
                                        case "\"sid\"":
                                            sid = Integer.parseInt (value);
                                            break;

                                        case "\"lang\"":
                                            lang = value;
                                            break;

                                        case "\"gender\"":
                                            gender = value;
                                            break;

                                        case "\"name\"":
                                            name = value;
                                            break;

                                        case "\"displayName\"":
                                            displayName = value;
                                            break;
                                    }
                                }
                            }
                            if (lang != null && gender != null && name != null && displayName != null) {
                                availableVoices.add (new VoiceRole (sid, lang, name, gender, displayName));
                            }
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