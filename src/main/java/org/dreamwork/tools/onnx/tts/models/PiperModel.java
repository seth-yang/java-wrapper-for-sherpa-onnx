package org.dreamwork.tools.onnx.tts.models;

import com.k2fsa.sherpa.onnx.OfflineTts;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig;

import java.io.InputStream;

public class PiperModel extends AbstractMappedTtsModel {
    public PiperModel (String root) {
        super (root);
    }

    @Override
    protected InputStream getMappingResource () {
        return getClass ().getClassLoader ().getResourceAsStream ("kokoro-voices.json");
    }

    @Override
    protected OfflineTts generateTTS () {
        String model   = root + "/vits-piper-en_GB-cori-medium/en_GB-cori-medium.onnx";
        String tokens  = root + "/vits-piper-en_GB-cori-medium/tokens.txt";
        String dataDir = root + "/vits-piper-en_GB-cori-medium/espeak-ng-data";

        OfflineTtsVitsModelConfig vitsModelConfig =
                OfflineTtsVitsModelConfig.builder()
                        .setModel(model)
                        .setTokens(tokens)
                        .setDataDir(dataDir)
                        .build();

        OfflineTtsModelConfig modelConfig =
                OfflineTtsModelConfig.builder()
                        .setVits(vitsModelConfig)
                        .setNumThreads(1)
                        .setDebug(true)
                        .build();

        OfflineTtsConfig config = OfflineTtsConfig.builder().setModel(modelConfig).build();
        return new OfflineTts (config);
    }
}