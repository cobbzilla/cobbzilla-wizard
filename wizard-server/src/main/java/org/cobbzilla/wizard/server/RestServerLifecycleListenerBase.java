package org.cobbzilla.wizard.server;

import org.cobbzilla.wizard.server.config.RestServerConfiguration;

public class RestServerLifecycleListenerBase<C extends RestServerConfiguration> implements RestServerLifecycleListener<C> {

    @Override public void beforeStart(RestServer server) {}

    @Override public void onStart(RestServer server) {}

    @Override public void beforeStop(RestServer server) {}

    @Override public void onStop(RestServer server) {}

}
