package org.cobbzilla.wizard.server;

import org.cobbzilla.wizard.server.config.RestServerConfiguration;
import org.glassfish.grizzly.http.server.HttpServer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;

public interface RestServer<C extends RestServerConfiguration> {

    HttpServer startServer() throws IOException;
    boolean isRunning ();

    C getConfiguration();
    void setConfiguration(C configuration);

    // If this returns a non-null value, we'll set the value of "tmpdir" in the Configuration and in FileUtil
    // If this returns a null value, we'll look for a value in an env var based on the server name (converted to snake case, upper-cased). It will be logged if not found.
    // If nothing else if found, we'll use FileUtil.defaultTempDir
    String getDefaultTmpdirEnvVar();

    ConfigurableApplicationContext buildSpringApplicationContext();
    ConfigurableApplicationContext buildSpringApplicationContext(final ApplicationContextConfig ctxConfig);

    void stopServer();

    String getClientUri();

    ApplicationContext getApplicationContext();

    URI getBaseUri();

    void addLifecycleListener (RestServerLifecycleListener<C> listener);
    default void addLifecycleListeners (Collection<RestServerLifecycleListener<C>> listeners) {
        for (RestServerLifecycleListener<C> listener : listeners) addLifecycleListener(listener);
    }

    void removeLifecycleListener (RestServerLifecycleListener<C> listener);
    Collection<RestServerLifecycleListener<C>> removeAllLifecycleListeners ();

}
