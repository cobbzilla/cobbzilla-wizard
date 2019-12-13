package org.cobbzilla.wizard.analytics;

import static org.cobbzilla.util.http.HttpStatusCodes.NO_CONTENT;

public abstract class AnalyticsHandlerBase implements AnalyticsHandler {

    protected AnalyticsConfiguration config;
    @Override public void init(AnalyticsConfiguration config) { this.config = config; }

    @Override public int getHttpSuccess() { return NO_CONTENT; }

}
