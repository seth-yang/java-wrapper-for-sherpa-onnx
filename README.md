# java-wrapper-for-sherpa-onnx-tts
A java wrapper for sherpa onnx TTS.

This project uses [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx); 
please refer to the `sherpa-onnx` repository for more information.

Since the `sherpa-onnx` Java API is distributed via [JitPack](https://jitpack.io), 
this project—as well as any downstream projects utilizing this library—must also incorporate JitPack.
## Quick Start

### Prerequisites
1. JDK 1.8+
2. You need to add JitPack to your pom file.
```xml
<repositories>
    <repository>
        <id>jitpack</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```
3. Supported systems include
    - Windows 
      - x64
      - arm64
    - Linux
      - x64
      - aarch64
    - MacOSX
      - aarch64 
      - x64
4. You need to download the corresponding pre-trained model from
   [sherpa-onnx release page](https://github.com/k2-fsa/sherpa-onnx/releases).

### Dependency
```xml
<dependency>
    <groupId>io.github.seth-yang</groupId>
    <artifactId>java-wrapper-for-sherpa-onnx</artifactId>
    <version>1.0.0</version>
</dependency>
```

Or build from the source code.

```bash
git clone https://github.com/seth-yang/java-wrapper-for-sherpa-onnx.git
cd java-wrapper-for-sherpa-onnx
mvn clean install
```

Additionally, you need to import the `sherpa-onnx-native` package corresponding to 
your platform into your project; for example, for the Windows x64 platform, 
you require the following dependencies:
```xml
<dependency>
    <groupId>com.github.k2-fsa.sherpa-onnx</groupId>
    <artifactId>sherpa-onnx-native-lib-win-x64</artifactId>
    <version>v1.13.8</version>
</dependency>
```

This library uses SLF4J as the logging facade; 
please pay attention to the corresponding logging framework configuration.

## Examples
### Simple synthesis
```java
import org.dreamwork.tools.onnx.tts.ITTSListener;
import org.dreamwork.tools.onnx.tts.TTS;
import org.dreamwork.tools.onnx.tts.models.TtsModel;

import java.util.concurrent.TimeUnit;

public class SimpleExample {
    public static void main (String[] args) {
        TTS tts = new TTS ();
        tts.config ()
            // after this timeout, the tts object will enter IDLE state
            .timeout (500, TimeUnit.MILLISECONDS)
            .model (TtsModel.Matcha) // select a model
            // point to the model root
            .modelRoot ("../models/matcha-icefall-zh-baker")
            .enableRealtimeMode ();
        
        tts.setListener (new ITTSListener () {
            @Override
            public void idle () {
                // when the tts object enter IDLE state, destroy the tts object
                tts.dispose ();
            }
        });

        tts.synthesis ("在一个阳光明媚的夏天，小马、小羊和小狗它们一块儿在广阔的草地上，嬉戏玩耍，这时小猴来了，还带着它心爱的足球活蹦乱跳地跑前、跑后教小马、小羊、小狗踢足球。");
    }
}
```

### Select voice role
you can change voice role if the model allowed
```java
import org.dreamwork.tools.onnx.tts.ITTSListener;
import org.dreamwork.tools.onnx.tts.TTS;
import org.dreamwork.tools.onnx.tts.models.TtsModel;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class SelectVoiceRoleExample {
    private static int index = 0;
    public static void main (String[] args) {
        TTS tts = new TTS ();
        tts.config ()
                // after this timeout, the tts object will enter IDLE state
                .timeout (500, TimeUnit.MILLISECONDS)
                .model (TtsModel.Kokoro) // select a model
                // point to the model root
                .modelRoot ("../models/kokoro-multi-lang-v1_1")
                .enableRealtimeMode ();
        var roles = tts.config ().availableVoiceRolesByLang ("en_US");
        var list = new ArrayList<> (roles);

        tts.setListener (new ITTSListener () {
            @Override
            public void started (Object target) {
                System.out.println ("now says: " + list.get (index ++));
            }

            @Override
            public void idle () {
                tts.dispose ();
            }
        });

        for (var role : list) {
            tts.synthesis (String.format ("Hello, nice to meet you, I'm %s!", role.displayName), role);
        }
    }
}
```

### Synthesis and save the result to file
You can write the tts result voice into a file using `synthesisAndSave` method. In this case,
there is no need to enable real-time playback mode.
```java
import org.dreamwork.tools.onnx.tts.ITTSListener;
import org.dreamwork.tools.onnx.tts.TTS;
import org.dreamwork.tools.onnx.tts.models.TtsModel;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

public class SynthesisAndSaveFileExample {
    public static void main (String[] args) throws IOException {
        TTS tts = new TTS ();
        tts.config ()
                // after this timeout, the tts object will enter IDLE state
                .timeout (500, TimeUnit.MILLISECONDS)
                .model (TtsModel.Matcha) // select a model
                // point to the model root
                .modelRoot ("../models/matcha-icefall-zh-baker")
                ;
        tts.setListener (new ITTSListener () {
            @Override
            public void voiceSaved (Object target, Path path) {
                System.out.printf ("%s was synthesis and saved to %s%n", target, path);
                // the file written successfully, destroy the tts
                tts.dispose ();
            }
        });

        final var text = "我是快乐的小行家!";
        final var target = Paths.get ("/tmp/output.wav");
        tts.synthesisAndSave (text, 0, 1.0f, target);
    }
}
```

### Forward Example
You can forward the results to other endpoints over the network.
```java
import org.dreamwork.tools.onnx.tts.ITTSListener;
import org.dreamwork.tools.onnx.tts.TTS;
import org.dreamwork.tools.onnx.tts.models.TtsModel;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

public class ForwardExample {
    private static OutputStream out = null;
    public static void main (String[] args) {
        Socket socket;

        try {
            // Simulate a connection to the upstream server.
            // Or use nio for better performance.
            socket = new Socket ("remote-host", 12345);
            out = socket.getOutputStream ();
        } catch (IOException ex) {
            System.err.println ("cannot connect to remote host");
            return;
        }

        TTS tts = new TTS ();
        tts.config ()
                // after this timeout, the tts object will enter IDLE state
                .timeout (500, TimeUnit.MILLISECONDS)
                .model (TtsModel.Matcha) // select a model
                // point to the model root
                .modelRoot ("../models/matcha-icefall-zh-baker")
                .enableForwardMode ()
                .forward (out);
        tts.setListener (new ITTSListener () {
            @Override
            public void idle () {
                tts.dispose ();
                if (out != null) {
                    try {
                        out.close ();
                    } catch (IOException ignored) {}
                }
            }
        });

        tts.synthesis ("在一个阳光明媚的夏天，小马、小羊和小狗它们一块儿在广阔的草地上，嬉戏玩耍，这时小猴来了，还带着它心爱的足球活蹦乱跳地跑前、跑后教小马、小羊、小狗踢足球。");
    }
}

```
### Other Situations
You do not need create the TTS instance everywhere, In the most cases,  your code looks like
```java
private TTs tts;
@PostConstruct
public void init () {
    tts = new TTs ();
    // something others ...
}

@PreDestroy 
public void destroy () {
    if (tts != null) {
        tts.dispose ();
    }
    // ...
}
```

# Thanks
- [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx)