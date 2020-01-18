package org.cobbzilla.wizard.server.listener;

import airbrake.AirbrakeNoticeBuilder;
import airbrake.AirbrakeNotifier;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.buffer.CircularFifoBuffer;
import org.cobbzilla.util.daemon.ErrorApi;
import org.cobbzilla.util.daemon.ZillaRuntime;
import org.cobbzilla.wizard.server.RestServer;
import org.cobbzilla.wizard.server.RestServerBase;
import org.cobbzilla.wizard.server.RestServerLifecycleListenerBase;
import org.cobbzilla.wizard.server.config.ErrorApiConfiguration;

import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.string.StringUtil.ellipsis;
import static org.cobbzilla.util.system.Sleep.sleep;

@Slf4j
public class ErrbitConfigListener extends RestServerLifecycleListenerBase {

    @Override public void onStart(RestServer server) {
        if (!server.getConfiguration().hasErrorApi()) {
            log.warn("onStart: no error API configured, not starting ErrbitApi error sender");
            return;
        }
        final ErrbitApi errorApi = new ErrbitApi(server.getConfiguration().getErrorApi());
        RestServerBase.setErrorApi(errorApi);
        ZillaRuntime.setErrorApi(errorApi);
        errorApi.start();
        log.info("onStart: "+server.getConfiguration().getErrorApi());
    }

    @Slf4j
    static class ErrbitApi implements ErrorApi, Runnable {

        private static final String SLEEP_MESSAGE = ErrbitApi.class.getName()+": waiting for more errors";

        private final String key;
        private String env;
        private long sendInterval;

        private final CircularFifoBuffer dupCache;
        private final CircularFifoBuffer fifo;
        private AirbrakeNotifier notifier;

        ErrbitApi(ErrorApiConfiguration errorApi) {
            if (errorApi != null && errorApi.isValid()) {
                this.key = errorApi.getKey();
                this.env = errorApi.getEnv();
                this.sendInterval = errorApi.getSendInterval();
                this.dupCache = new CircularFifoBuffer(errorApi.getDupCacheSize());
                this.fifo = new CircularFifoBuffer(errorApi.getBufferSize());
                this.notifier = new AirbrakeNotifier(errorApi.getUrl());
            } else {
                log.warn("ErrbitApi: errorApi is null or invalid, not running");
                this.key = null;
                this.fifo = null;
                this.dupCache = null;
            }
        }

        @Override public void report(Exception e) {
            if (key != null) {
                log.error(e.toString());
                synchronized (fifo) { fifo.add(e); }
            } else {
                if (log.isWarnEnabled()) log.warn("report: could not send exception to error reporting API: "+e, e);
            }
        }

        @Override public void report(String s) {
            if (key != null) {
                if (log.isErrorEnabled()) log.error(s);
                synchronized (fifo) { fifo.add(s); }
            } else {
                if (log.isWarnEnabled()) log.warn("report: could not send exception to error reporting API: "+s);
            }
        }

        @Override public void report(String s, Exception e) {
            report(s+"\nException: "+shortError(e)+"\n"+getStackTrace(e));
        }

        public void start() {
            final Thread t = new Thread(this);
            t.setDaemon(true);
            t.setName(getClass().getSimpleName());
            t.start();
        }

        @Override public void run() {
            while (true) {
                try {
                    final List<Object> reports;
                    synchronized (fifo) {
                        if (fifo.isEmpty()) {
                            reports = null;
                        } else {
                            reports = new ArrayList<Object>(fifo);
                            fifo.clear();
                        }
                    }
                    if (reports == null) {
                        sleep(sendInterval, SLEEP_MESSAGE);
                        continue;
                    }
                    for (Object o : reports) {
                        if (o == null) {
                            if (log.isErrorEnabled()) log.error("ErrbitApi.run: NOT reporting null object");  // should never happen

                        } else if (o instanceof Exception) {
                            if (log.isDebugEnabled()) log.debug("ErrbitApi.run: reporting Exception: "+shortError((Exception) o));
                            sendReport((Exception) o);

                        } else if (o instanceof String) {
                            if (log.isDebugEnabled()) log.debug("ErrbitApi.run: reporting String: "+o);
                            sendReport((String) o);

                        } else {
                            final String val = o.toString();
                            if (log.isWarnEnabled()) log.warn("ErrbitApi.run: reporting object that is neither Exception nor String (" + o.getClass().getName() + ") as String: " + val);
                            sendReport(val);
                        }
                    }
                } catch (Exception e) {
                    if (log.isErrorEnabled()) log.error("ErrbitApi.run: unexpected exception: "+shortError(e));
                }
            }
        }

        public void sendReport(Exception e) { sendReport(fullError(e)); }

        public void sendReport(String s) {
            if (inCache(s)) {
                if (log.isWarnEnabled()) log.warn("sendReport(" + ellipsis(s, 100) + "): already reported recently, not resending");
                return;
            }
            final AirbrakeNoticeBuilder builder = new AirbrakeNoticeBuilder(key, s, env);
            final int responseCode = notifier.notify(builder.newNotice());
            if (responseCode != 200) {
                if (log.isWarnEnabled()) log.warn("sendReport("+ellipsis(s, 100)+"): notifier API returned "+responseCode);
            }
        }

        public boolean inCache(String s) {
            synchronized (dupCache) {
                if (dupCache.contains(s)) return true;
                dupCache.add(s);
            }
            return false;
        }

    }

}
