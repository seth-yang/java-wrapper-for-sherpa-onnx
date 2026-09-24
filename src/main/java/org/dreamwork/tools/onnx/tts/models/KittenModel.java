package org.dreamwork.tools.onnx.tts.models;

import com.k2fsa.sherpa.onnx.OfflineTts;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsKittenModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig;

import java.io.InputStream;

public class KittenModel extends AbstractMappedTtsModel {
    public KittenModel (String root) {
        super (root);
    }

    @Override
    protected InputStream getMappingResource () {
        return getClass ().getClassLoader ().getResourceAsStream ("single-voices.json");
    }

    @Override
    public OfflineTts generateTTS () {
        String model = root + "/kitten-nano-en-v0_1-fp16/model.fp16.onnx";
        String voices = root + "/kitten-nano-en-v0_1-fp16/voices.bin";
        String tokens = root + "/kitten-nano-en-v0_1-fp16/tokens.txt";
        String dataDir = root + "/kitten-nano-en-v0_1-fp16/espeak-ng-data";
        OfflineTtsKittenModelConfig kittenModelConfig =
                OfflineTtsKittenModelConfig.builder ()
                        .setModel (model)
                        .setVoices (voices)
                        .setTokens (tokens)
                        .setDataDir (dataDir)
                        .build ();

        OfflineTtsModelConfig modelConfig =
                OfflineTtsModelConfig.builder ()
                        .setKitten (kittenModelConfig)
                        .setNumThreads (2)
                        .setDebug (true)
                        .build ();

        OfflineTtsConfig config = OfflineTtsConfig.builder ().setModel (modelConfig).build ();
        return new OfflineTts (config);
    }
}
