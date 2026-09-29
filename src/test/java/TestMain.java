import org.dreamwork.tools.onnx.tts.ITTSListener;
import org.dreamwork.tools.onnx.tts.TTS;
import org.dreamwork.tools.onnx.tts.models.TtsModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

public class TestMain {
    private static final Logger logger = LoggerFactory.getLogger (TestMain.class);
    public static void main (String[] args) throws Exception {
/*
        final PipedInputStream pin = new PipedInputStream ();
        final PipedOutputStream pout = new PipedOutputStream (pin);
*/
        final TTS tts = new TTS ();

        final ITTSListener listener = new ITTSListener () {
            @Override
            public void started (Object target) {
                System.out.println ("---------------------------------------------------- starting ---------------------------------------");
                if (target instanceof String) {
                    System.out.println ("text: " + target + " started.");
                } else if (target instanceof Path) {
                    System.out.println ("path: " + target + " started.");
                } else {
                    System.out.println ("starting play input stream");
                }
            }

            @Override
            public void finished (Object target) {
                System.out.println ("stopped.");
            }

            @Override
            public void idle () {
                System.out.println ("into idle");
                System.out.println (System.currentTimeMillis ());
                tts.dispose ();
            }

            @Override
            public void voiceSaved (Object target, Path path) {
                logger.info ("{} saved", path);
            }

            @Override
            public void handleException (Object target, Throwable ex) {
                System.err.println ("something gets wrong");
                ex.printStackTrace (System.err);
            }
        };


        tts.config ()
                .model (TtsModel.Matcha)
                .modelRoot ("F:\\temp\\sherpa-onnx\\models\\matcha-icefall-zh-baker")
//                .modelRoot ("F:\\temp\\sherpa-onnx\\models\\matcha-icefall-zh-baker")
                .enableRealtimeMode ()
//                .speed (1.25f)
//                .enableForwardMode ()
//                .forward (pout)
                .timeout (500, TimeUnit.MILLISECONDS)
//                .voice (4)
        ;

/*
        player = new PcmRealtimePlayer (tts.config ().sampleRate (), 500);
        player.start ();
*/
/*
        ExecutorService executor = Executors.newSingleThreadExecutor ();
        executor.execute (() -> {
            try {
                byte[] buff = new byte[4096];
                String uuid = StringUtil.uuid ();
                int length;
                while ((length = pin.read (buff, 0, 4096)) != -1) {
                    byte[] pcm = new byte[length];
                    System.arraycopy (buff, 0, pcm, 0, length);
                    player.play (new SamplesWrapper (uuid, pcm));
                }
            } catch (Exception ex) {
                logger.error (ex.getMessage (), ex);
            }
        });
        executor.shutdown ();
*/

        tts.setListener (listener);

/*
        String longText =
                "啊，那就破案了。\n"
              + "这比\"多线程共享同一个实例\"还要严格——sherpa-onnx 的 OfflineTts 要求构造和 generate 必须在同一个线程，本质上是 native 层持有了线程相关的状态（很可能是 onnxruntime 的 session 绑定了创建线程的 allocator / thread-local 环境）。\n"
              + "所以你的结论完全正确，而且这解释了为什么：\n"
              + "单线程跑没问题\n"
              + "线程池里 mailLoop 创建、pool-1-thread-1 里 generate → 崩\n"
              + "哪怕每个任务 new 一个新实例，但 new 在提交线程、generate 在 worker 线程 → 照样崩"
                ;

        tts.synthesis (longText);
*/
        String[] lines = {
                "当夜幕降临，星光点点，伴随着微风拂面，我在静谧中感受着时光的流转，思念如涟漪荡漾，梦境如画卷展开，我与自然融为一体，沉静在这片宁静的美丽之中，感受着生命的奇迹与温柔.",
                "某某银行的副行长和一些行政领导表示，他们去过长江和长白山; 经济不断增长。2024年12月31号，拨打110或者18920240511。123456块钱。",
                "所以你的结论完全正确，而且这解释了为什么",
                "啊，那就破案了",
                "单线程跑没问题",
                "多线程共享同一个实例"
        };
        Path path = Paths.get ("f:/temp/abc.wav");
        tts.synthesisAndSave (lines[0], 0, 1.0f, path);

        for (String line : lines) {
            tts.synthesis (line, 0);
        }
    }
}