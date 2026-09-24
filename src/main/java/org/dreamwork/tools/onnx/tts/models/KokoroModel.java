package org.dreamwork.tools.onnx.tts.models;

import com.k2fsa.sherpa.onnx.OfflineTts;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig;
import org.dreamwork.tools.onnx.tts.VoiceRole;

import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;

public class KokoroModel extends AbstractMappedTtsModel {
    public KokoroModel (String root) {
        super (root);
    }

    @Override
    protected InputStream getMappingResource () {
        return getClass ().getClassLoader ().getResourceAsStream ("kokoro-voices.json");
    }

    @Override
    public Collection<VoiceRole> getAvailableVoices () {
        return Collections.emptyList ();
    }

    @Override
    public OfflineTts generateTTS () {
        if (root == null || root.isEmpty ()) {
            throw new RuntimeException ("model root did not set！");
        }
        String model   = root + "/kokoro-multi-lang-v1_0/model.onnx";
        String voices  = root + "/kokoro-multi-lang-v1_0/voices.bin";
        String tokens  = root + "/kokoro-multi-lang-v1_0/tokens.txt";
        String dataDir = root + "/kokoro-multi-lang-v1_0/espeak-ng-data";
        String lexicon = root + "/kokoro-multi-lang-v1_0/lexicon-us-en.txt,./kokoro-multi-lang-v1_0/lexicon-zh.txt";

        OfflineTtsKokoroModelConfig kokoroModelConfig =
                OfflineTtsKokoroModelConfig.builder()
                        .setModel(model)
                        .setVoices(voices)
                        .setTokens(tokens)
                        .setDataDir(dataDir)
                        .setLexicon(lexicon)
                        .build();

        OfflineTtsModelConfig modelConfig =
                OfflineTtsModelConfig.builder()
                        .setKokoro(kokoroModelConfig)
                        .setNumThreads(2)
                        .setDebug(true)
                        .build();

        OfflineTtsConfig config = OfflineTtsConfig.builder().setModel(modelConfig).build();
        return new OfflineTts (config);
    }
}