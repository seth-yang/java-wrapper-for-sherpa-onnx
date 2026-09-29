package org.dreamwork.tools.onnx.tts.models;

import com.k2fsa.sherpa.onnx.OfflineTts;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig;

import java.io.InputStream;

public class KokoroModel extends AbstractMappedTtsModel {
    public KokoroModel (String root) {
        super (root);
    }

    @Override
    protected InputStream getMappingResource () {
        return getClass ().getClassLoader ().getResourceAsStream ("kokoro-voices.json");
    }

    @Override
    public OfflineTts generateTTS () {
        if (root == null || root.isEmpty ()) {
            throw new RuntimeException ("model root did not set！");
        }

        OfflineTtsKokoroModelConfig kokoroModelConfig =
                OfflineTtsKokoroModelConfig.builder()
                        .setModel   (root + "/model.onnx")
                        .setVoices  (root + "/voices.bin")
                        .setTokens  (root + "/tokens.txt")
                        .setDataDir (root + "/espeak-ng-data")
                        .setLexicon (root + "/lexicon-us-en.txt," + root + "/lexicon-zh.txt")
                        .build();

        OfflineTtsModelConfig modelConfig =
                OfflineTtsModelConfig.builder()
                        .setKokoro(kokoroModelConfig)
                        .setNumThreads(4)
                        .setDebug(false)
                        .build();

        OfflineTtsConfig config = OfflineTtsConfig.builder()
                .setModel(modelConfig)
                .setMaxNumSentences (1)
                .build();
        return new OfflineTts (config);
    }
}