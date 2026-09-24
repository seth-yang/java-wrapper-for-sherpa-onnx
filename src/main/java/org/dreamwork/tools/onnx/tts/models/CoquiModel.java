package org.dreamwork.tools.onnx.tts.models;

import com.k2fsa.sherpa.onnx.OfflineTts;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig;

import java.io.InputStream;

public class CoquiModel extends AbstractMappedTtsModel {
    public CoquiModel (String root) {
        super (root);
    }

    @Override
    protected InputStream getMappingResource () {
        return getClass ().getClassLoader ().getResourceAsStream ("single-voices.json");
    }

    @Override
    protected OfflineTts generateTTS () {
        if (root == null || root.trim ().isEmpty ()) {
            throw new RuntimeException ("no model root set");
        }

        String model = String.format ("%s/vits-coqui-de-css10/model.onnx", root);
        String tokens = String.format ("%s/vits-coqui-de-css10/tokens.txt", root);

        OfflineTtsVitsModelConfig vitsModelConfig =
                OfflineTtsVitsModelConfig.builder().setModel(model).setTokens(tokens).build();

        OfflineTtsModelConfig modelConfig =
                OfflineTtsModelConfig.builder()
                        .setVits(vitsModelConfig)
                        .setNumThreads(1)
                        .setDebug(true)
                        .build();

        OfflineTtsConfig config = OfflineTtsConfig.builder().setModel(modelConfig).build();
        return new OfflineTts(config);
    }
}
