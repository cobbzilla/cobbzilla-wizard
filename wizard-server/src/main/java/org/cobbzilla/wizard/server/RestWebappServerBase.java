package org.cobbzilla.wizard.server;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.network.PortPicker;
import org.cobbzilla.wizard.server.config.HttpConfiguration;
import org.cobbzilla.wizard.server.config.RestServerConfiguration;
import org.cobbzilla.wizard.server.config.WebappConfiguration;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.ArrayList;
import java.util.List;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;

@Slf4j
public class RestWebappServerBase<C extends RestServerConfiguration> extends RestServerBase<C> {

    @Getter(lazy=true) private final Server jetty = initJetty();
    private Server initJetty() {
        final HttpConfiguration httpConfiguration = getConfiguration().getHttp();
        if (!httpConfiguration.hasWebPort()) {
            httpConfiguration.setWebPort(PortPicker.pickOrDie());
        }
        return new Server(httpConfiguration.getWebPort());
    }

    @Override protected void initWebapps(C configuration, ConfigurableApplicationContext applicationContext) {
        final ContextHandlerCollection contexts = new ContextHandlerCollection();
        final List<Handler> handlers = new ArrayList<>();
        for (WebappConfiguration config : configuration.getWebapps()) {
            handlers.add(config.deploy(applicationContext));
        }
        try {
            contexts.setHandlers((Handler[]) handlers.toArray());
            final Server jetty = getJetty();
            jetty.setHandler(contexts);
            jetty.setStopAtShutdown(true);
            jetty.start();
        } catch (Exception e) {
            die("initWebapps: error starting Jetty to run webapps: "+e, e);
        }
    }

    @Override protected void stopWebapps() {
        try {
            getJetty().stop();
        } catch (Exception e) {
            log.warn("stopWebapps: error stopping jetty: "+e, e);
        }
    }
}
