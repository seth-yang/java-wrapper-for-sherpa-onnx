package org.dreamwork.tools.onnx.tts.models;

import com.k2fsa.sherpa.onnx.OfflineTts;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsMatchaModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig;

import java.io.InputStream;
import java.util.Arrays;

public class MatchaModel extends AbstractMappedTtsModel {
    public MatchaModel (String root) {
        super (root);
    }

    @Override
    protected InputStream getMappingResource () {
        return getClass ().getClassLoader ().getResourceAsStream ("single-voices.json");
    }

    @Override
    protected OfflineTts generateTTS () {
        String acousticModel = "./matcha-icefall-zh-baker/model-steps-3.onnx";
        String vocoder = root + "/vocos-22khz-univ.onnx";
        String tokens  = root + "/matcha-icefall-zh-baker/tokens.txt";
        String lexicon = root + "/matcha-icefall-zh-baker/lexicon.txt";
        String ruleFsts = Arrays.toString (new String [] {
                root + "/matcha-icefall-zh-baker/phone.fst",
                root + "/matcha-icefall-zh-baker/date.fst",
                root + "/matcha-icefall-zh-baker/number.fst"
        });

        OfflineTtsMatchaModelConfig matchaModelConfig =
                OfflineTtsMatchaModelConfig.builder()
                        .setAcousticModel(acousticModel)
                        .setVocoder(vocoder)
                        .setTokens(tokens)
                        .setLexicon(lexicon)
                        .build();

        OfflineTtsModelConfig modelConfig =
                OfflineTtsModelConfig.builder()
                        .setMatcha(matchaModelConfig)
                        .setNumThreads(1)
                        .setDebug(true)
                        .build();

        OfflineTtsConfig config =
                OfflineTtsConfig.builder().setModel(modelConfig).setRuleFsts(ruleFsts).build();
        return new OfflineTts (config);
    }
}
