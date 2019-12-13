package org.cobbzilla.wizard.log;

import ch.qos.logback.core.OutputStreamAppender;
import lombok.Getter;
import lombok.Setter;
import org.cobbzilla.util.system.Bytes;
import org.cobbzilla.wizard.server.config.RestServerConfiguration;

import java.io.*;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.reflect.ReflectionUtil.closeQuietly;
import static org.cobbzilla.util.system.Sleep.sleep;

public class LogRelayAppender<E> extends OutputStreamAppender<E> {

    @Getter @Setter private static volatile RestServerConfiguration config;

    public static final long STARTUP_TIMEOUT = SECONDS.toMillis(30);
    private static final int PIPE_BUFSIZ = (int) (8*Bytes.MB);

    private PipedInputStream in;
    private PipedOutputStream out;

    @Override public void stop() {
        if (in != null) closeQuietly(in);
        if (out != null) closeQuietly(out);
        super.stop();
    }

    // allows lambda to call our superclass's start method
    private void superStart () { super.start(); }

    @Override public void start() {
        final String simpleClass = getClass().getSimpleName();
        daemon(() -> {
            final long start = now();

            // wait for config to get set (someone has to initialize us after spring is up)
            while ((config == null || config.getApplicationContext() == null) && (now() - start < STARTUP_TIMEOUT)) {
                sleep(SECONDS.toMillis(1));
            }
            if (config == null) {
                System.err.println(simpleClass+": RestServerConfiguration was never set, exiting");
                stop(); return;
            }
            if (config.getApplicationContext() == null) {
                System.err.println(simpleClass+": RestServerConfiguration.applicationContext was never set, exiting");
                stop(); return;
            }

            boolean done = false;
            while (!done) {
                try {
                    if (!relayLogs(simpleClass)) done = true;
                } catch (Exception e) {
                    System.err.println(simpleClass+": error relaying logs: "+e);
                } finally {
                    if (!done) System.err.println(simpleClass+": retrying setup/relay logs");
                    stop();
                }
            }
        });
    }

    public boolean relayLogs(String simpleClass) {
        try {
            in = new PipedInputStream(PIPE_BUFSIZ);
            out = new PipedOutputStream(in);
            setOutputStream(out);
        } catch (IOException e) {
            System.err.println("start: error setting up pipes: " + e);
            return false;
        }

        BufferedReader reader = null;
        try {
            try {
                reader = new BufferedReader(new InputStreamReader(in));
            } catch (Exception e) {
                System.err.println(simpleClass + ": error setting up reader, exiting");
                return false;
            }
            final LogRelayAppenderConfig logRelayConfig = config.getLogRelay();
            if (logRelayConfig == null) {
                System.err.println(simpleClass + ": no relayConfig was found, exiting");
                return false;
            }
            final String relayTo = logRelayConfig.getRelayTo();
            if (empty(relayTo)) {
                System.err.println(simpleClass + ": relayConfig was found, but relayTo was empty, exiting");
                return false;
            }
            final LogRelayAppenderTarget relayTarget;
            try {
                // cast shouldn't be required, but a compilation error occurs if we remove it
                relayTarget = (LogRelayAppenderTarget) config.getBean(relayTo);
                if (!relayTarget.init(logRelayConfig.getParams())) {
                    System.err.println(simpleClass + ": relayTo (" + relayTo + ") disabled, exiting");
                    return false;
                }
            } catch (Exception e) {
                System.err.println(simpleClass + ": error initializing relayTo (" + relayTo + "), exiting: " + e);
                return false;
            }

            superStart();

            String line = null;
            try {
                while ((line = reader.readLine()) != null) relayTarget.relay(line);
            } catch (IOException e) {
                System.err.println(simpleClass + ": error relaying to LogRelayAppenderTarget spring bean: " + relayTarget.getClass().getName()+" (will retry): "+e);
            }
            return true;

        } finally {
            closeQuietly(reader);
        }
    }

}
