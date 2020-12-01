package org.cobbzilla.wizard.server.listener;

import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.http.URIUtil;
import org.cobbzilla.wizard.server.RestServer;
import org.cobbzilla.wizard.server.RestServerLifecycleListenerBase;
import org.cobbzilla.wizard.server.config.RestServerConfiguration;

import java.awt.*;
import java.util.Map;

import static java.awt.Desktop.isDesktopSupported;
import static java.lang.Boolean.parseBoolean;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

@Slf4j
public class BrowserLauncherListener extends RestServerLifecycleListenerBase {

    public static final String DEFAULT_DISABLE_BROWSER_LAUNCH_ENV_VAR = "DISABLE_BROWSER_AUTO_LAUNCH";

    public String getDisableBrowserAutoLaunchEnvVarName() { return DEFAULT_DISABLE_BROWSER_LAUNCH_ENV_VAR; }

    @Override public void onStart(RestServer server) {
        final RestServerConfiguration config = server.getConfiguration();
        final String baseUri = config.getPublicUriBase();
        final Thread appThread = new Thread(() -> {
            final boolean allowLaunch = configAllowsBrowserLaunch(config);
            final Desktop desktop = allowLaunch && isDesktopSupported() ? Desktop.getDesktop() : null;
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
                final String versionInfo = server.getConfiguration().hasVersion()
                        ? "\nVersion: " + server.getConfiguration().getVersion()
                        : "";
                log.info("\n\n"+server.getConfiguration().getServerName()+" Successfully Started"+versionInfo+"\n\nNot launching browser: System lacks a browser and/or desktop window manager.\n\nWeb UI is: "+baseUri+"\nAPI is: "+baseUri+"/api\nHit Control-C to stop the server\n");
            }
        });
        appThread.setName(BrowserLauncherListener.class.getSimpleName());
        appThread.setDaemon(true);
        appThread.start();

        super.onStart(server);
    }

    public boolean configAllowsBrowserLaunch(RestServerConfiguration config) {
        final Map<String, String> env = config.getEnvironment();
        final String varName = getDisableBrowserAutoLaunchEnvVarName();
        if (empty(varName)) {
            log.warn("configAllowsBrowserLaunch: getDisableBrowserAutoLaunchEnvVarName() returned empty, not disabling");
            return true;
        }
        return !parseBoolean(env.get(varName));
    }

}
