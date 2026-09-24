package org.dreamwork.tools.onnx.tts;

import com.k2fsa.sherpa.onnx.OfflineTts;
import org.dreamwork.util.IDisposable;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class TTS implements IDisposable, AutoCloseable {
    private TTSConfig config;
    private OfflineTts tts;

    private volatile ITTSListener listener;
    private volatile PcmRealtimePlayer player;

    private final BlockingQueue<String> queue = new ArrayBlockingQueue<> (1024);

    public TTSConfig config () {
        return config;
    }

    public void synthesis (String text) {
        if (tts == null) {
            tts = config.build ();
        }
        if ((config.mode & TTSConfig.MODE_REALTIME) != 0) {
            if (player == null) {
                player = new PcmRealtimePlayer (config ().sampleRate);
                player.setListener (new PcmRealtimePlayer.IPlayerListener () {
                    @Override
                    public void onStart (String id) {

                    }

                    @Override
                    public void onInterrupted (String id) {

                    }

                    @Override
                    public void onComplete (String id) {

                    }
                });
            }
        }


    }

    public void synthesis (String text, VoiceRole voice) {
        config.voice (voice);
        synthesis (text);
    }

    public void synthesis (String text, int voice) {
        config.voice (voice);
        synthesis (text);
    }

    public static void main (String[] args) {
        try (TTS tts = new TTS ()) {
            tts.config.model (null)
                    .enableRealtimeMode ()
                    .voice (46)
            ;
            tts.synthesis ("Hi there!");
        }
    }

    @Override
    public void close () {
        dispose ();
    }

    @Override
    public void dispose () {
        if (tts != null) {
            tts.release ();
        }
        if (player != null) {
            player.stop ();
        }
    }
}