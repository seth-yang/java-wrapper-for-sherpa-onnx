package org.dreamwork.tools.onnx.tts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

class TTSEventLoop {
    private final Logger logger = LoggerFactory.getLogger (TTSEventLoop.class);
    private final BlockingQueue<InnerRunner> tasks = new ArrayBlockingQueue<> (64);

    private volatile boolean running = true;
    private volatile ITTSListener listener = null;

    void setListener (ITTSListener listener) {
        this.listener = listener;
    }

    boolean raise (InnerRunner task) {
        return tasks.offer (task);
    }

    void mainLoop () {
        Thread thread = Thread.currentThread ();
        thread.setName ("tts.event.loop");
        while (running && !thread.isInterrupted ()) {
            InnerRunner runner;
            try {
                runner = tasks.poll (200, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread ().interrupt ();
                continue;
            }

            if (runner != null) {
                try {
                    runner.runner.run ();
                } catch (Throwable ex) {
                    logger.warn (ex.getMessage (), ex);
                    if (listener != null) {
                        try {
                            listener.handleException (runner.id, ex);
                        } catch (Throwable t) {
                            logger.error (t.getMessage (), t);
                        }
                    }
                }
            }
        }
        logger.info ("the event loop stopped.");
    }

    void stop () {
        if (logger.isTraceEnabled ()) {
            logger.trace ("stopping the event loop ...");
        }
        running = false;
        tasks.clear ();
    }

    static final class InnerRunner {
        final String id;
        final Runnable runner;

        public InnerRunner (String id, Runnable runner) {
            this.id = id;
            this.runner = runner;
        }
    }
}