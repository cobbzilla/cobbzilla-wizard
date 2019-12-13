package org.cobbzilla.wizard.server.listener;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.cobbzilla.util.daemon.ErrorApi;
import org.cobbzilla.util.daemon.ZillaRuntime;
import org.cobbzilla.wizard.server.RestServer;
import org.cobbzilla.wizard.server.RestServerBase;
import org.cobbzilla.wizard.server.RestServerLifecycleListenerBase;
import org.cobbzilla.wizard.server.config.RestServerConfiguration;

@Slf4j
public class ErrbitConfigListener extends RestServerLifecycleListenerBase {

    @Override public void onStart(RestServer server) {
        final ErrbitApi errorApi = new ErrbitApi(server.getConfiguration());
        RestServerBase.setErrorApi(errorApi);
        ZillaRuntime.setErrorApi(errorApi);
        log.info("onStart: "+server.getConfiguration().getErrorApi());
    }

    @AllArgsConstructor @Slf4j
    static class ErrbitApi implements ErrorApi {

        private final RestServerConfiguration config;

        @Override public void report(Exception e) {
            if (config.hasErrorApi()) {
                log.error(e.toString());
                config.getErrorApi().report(e);
            } else {
                log.warn("report: could not send exception to error reporting API: "+e, e);
            }
        }

        @Override public void report(String s) {
            if (config.hasErrorApi()) {
                log.error(s);
                config.getErrorApi().report(s);
            } else {
                log.warn("report: could not send exception to error reporting API: "+s);
            }
        }

        @Override public void report(String s, Exception e) {
            report(s+"\nException: "+e+"\n"+ExceptionUtils.getStackTrace(e));
        }
    }

}
