package org.cobbzilla.wizard.server.listener;

import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.http.URIUtil;
import org.cobbzilla.wizard.server.RestServer;
import org.cobbzilla.wizard.server.RestServerLifecycleListenerBase;
import org.cobbzilla.wizard.server.config.RestServerConfiguration;

import java.awt.*;

import static java.awt.Desktop.isDesktopSupported;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;

@Slf4j
public class BrowserLauncherListener extends RestServerLifecycleListenerBase {

    @Override public void onStart(RestServer server) {
        final RestServerConfiguration config = server.getConfiguration();
        final String baseUri = config.getPublicUriBase();
        final Thread appThread = new Thread(new Runnable() {
            @Override public void run() {
                final Desktop desktop = isDesktopSupported() ? Desktop.getDesktop() : null;
                if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
                    try {
                        desktop.browse(URIUtil.toUri(baseUri));
                    } catch (Exception e) {
                        final String msg = "onStart: error launching default browser with url '" + baseUri + "':  " + e;
                        log.error(msg, e);
                        die(msg, e);
                    }
                } else {
                    // no browser. tell the user where the server is listening via log statement
                    log.info("\n\n"+server.getConfiguration().getServerName()+" Successfully Started\n\nNot launching browser: System lacks a browser and/or desktop window manager.\n\nWeb UI is: "+baseUri+"\nAPI is: "+baseUri+"/api\nHit Control-C to stop the server\n");
                }
            }
        });
        appThread.setName(BrowserLauncherListener.class.getSimpleName());
        appThread.setDaemon(true);
        appThread.start();

        super.onStart(server);
    }
}
