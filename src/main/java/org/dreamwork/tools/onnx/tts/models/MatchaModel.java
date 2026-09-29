package org.dreamwork.tools.onnx.tts.models;

import com.k2fsa.sherpa.onnx.OfflineTts;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsMatchaModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig;
import org.dreamwork.tools.onnx.tts.VoiceRole;

import java.io.InputStream;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

public class MatchaModel extends AbstractMappedTtsModel {
    private final AtomicBoolean changed = new AtomicBoolean (false);
    public MatchaModel (String root) {
        super (root);
    }

    @Override
    public Collection<VoiceRole> getAvailableVoices () {
        if (changed.compareAndSet (false, true)) {
            VoiceRole role = availableVoices.get (0);
            availableVoices.clear ();
            availableVoices.add (new VoiceRole (role.sid, "zh_CN", role.name, role.gender, role.displayName));
        }
        return availableVoices;
    }

    @Override
    protected InputStream getMappingResource () {
        return getClass ().getClassLoader ().getResourceAsStream ("single-voices.json");
    }

    @Override
    protected OfflineTts generateTTS () {
        String acousticModel = root + "/model-steps-3.onnx";
        String vocoder = root + "/vocos-22khz-univ.onnx";
        String tokens  = root + "/tokens.txt";
        String lexicon = root + "/lexicon.txt";
        String ruleFsts =
                root + "/phone.fst," +
                root + "/date.fst," +
                root + "/number.fst";

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
                        .setDebug(false)
                        .build();

        OfflineTtsConfig config =
                OfflineTtsConfig.builder().setModel(modelConfig).setRuleFsts(ruleFsts).build();
        return new OfflineTts (config);
    }
}
