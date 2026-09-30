import org.dreamwork.tools.onnx.tts.util.WaveStreamAdapter;
import org.dreamwork.util.ThreadHelper;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.function.Consumer;

public class Resample {
    public static void main (String[] args) throws Exception {
        AudioFormat targetFmt = new AudioFormat (
                AudioFormat.Encoding.PCM_SIGNED,
                24000, 16, 1, 2, 24000, false
        );

        SourceDataLine line = AudioSystem.getSourceDataLine (targetFmt);
        line.open (targetFmt);
        line.start ();

        WaveStreamAdapter adapter = new WaveStreamAdapter (targetFmt);
        Consumer<byte[]> player = buff -> line.write (buff, 0, buff.length);

        final String[] names = new String[] {"test_16000_mono.wav", "test_24000_mono.wav", "test_48000_stereo.wav"};
        Arrays.stream (names).forEach (name -> {
            try {
                System.out.printf ("start resample %s\n", name);
                File file = new File ("f:\\temp", name);
                adapter.resample (Files.newInputStream (file.toPath ()), player);
                System.out.printf ("%s resample complete%n", name);
                ThreadHelper.delay (2000);
            } catch (Exception ex) {
                ex.printStackTrace ();
            }
        });

        line.drain ();
        line.stop ();
        line.close ();
    }
}