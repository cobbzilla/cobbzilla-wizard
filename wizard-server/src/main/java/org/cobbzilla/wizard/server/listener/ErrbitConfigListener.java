package org.cobbzilla.wizard.server.listener;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.buffer.CircularFifoBuffer;
import org.cobbzilla.util.daemon.ErrorApi;
import org.cobbzilla.util.daemon.ZillaRuntime;
import org.cobbzilla.wizard.server.RestServer;
import org.cobbzilla.wizard.server.RestServerBase;
import org.cobbzilla.wizard.server.RestServerLifecycleListenerBase;
import org.cobbzilla.wizard.server.config.RestServerConfiguration;

import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;
import static org.cobbzilla.util.system.Sleep.sleep;

@Slf4j
public class ErrbitConfigListener extends RestServerLifecycleListenerBase {

    @Override public void onStart(RestServer server) {
        if (!server.getConfiguration().hasErrorApi()) {
            log.warn("onStart: no error API configured, not starting ErrbitApi error sender");
            return;
        }
        final ErrbitApi errorApi = new ErrbitApi(server.getConfiguration());
        RestServerBase.setErrorApi(errorApi);
        ZillaRuntime.setErrorApi(errorApi);
        errorApi.start();
        log.info("onStart: "+server.getConfiguration().getErrorApi());
    }

    @Slf4j
    static class ErrbitApi implements ErrorApi, Runnable {

        private static final String SLEEP_MESSAGE = ErrbitApi.class.getName()+"waiting for more errors";

        private final RestServerConfiguration config;
        private final CircularFifoBuffer fifo;

        ErrbitApi(RestServerConfiguration config) {
            this.config = config;
            this.fifo = new CircularFifoBuffer(config.getErrorApi().getBufferSize());
        }

        @Override public void report(Exception e) {
            if (config.hasErrorApi()) {
                log.error(e.toString());
                synchronized (fifo) { fifo.add(e); }
            } else {
                log.warn("report: could not send exception to error reporting API: "+e, e);
            }
        }

        @Override public void report(String s) {
            if (config.hasErrorApi()) {
                log.error(s);
                synchronized (fifo) { fifo.add(s); }
            } else {
                log.warn("report: could not send exception to error reporting API: "+s);
            }
        }

        @Override public void report(String s, Exception e) {
            report(s+"\nException: "+e+"\n"+getStackTrace(e));
        }

        public void start() {
            final Thread t = new Thread(this);
            t.setDaemon(true);
            t.setName(getClass().getName());
            t.start();
        }

        @Override public void run() {
            while (true) {
                try {
                    final List reports;
                    synchronized (fifo) {
                        if (fifo.isEmpty()) {
                            reports = new ArrayList(fifo);
                            fifo.clear();
                        } else {
                            reports = null;
                        }
                    }
                    if (reports == null) {
                        sleep(config.getErrorApi().getSendInterval(), SLEEP_MESSAGE);
                        continue;
                    }
                    for (Object o : reports) {
                        if (o instanceof Exception) {
                            config.getErrorApi().report((Exception) o);
                        } else if (o instanceof String) {
                            config.getErrorApi().report((String) o);
                        } else {
                            final String val = o.toString();
                            log.warn("ErrbitApi.run: reporting object that is neither Exception nor String (" + o.getClass().getName() + ") as String: " + val);
                            config.getErrorApi().report(val);
                        }
                    }
                } catch (Exception e) {
                    log.error("ErrbitApi.run: unexpected exception: "+shortError(e));
                }
            }
        }
    }

}
