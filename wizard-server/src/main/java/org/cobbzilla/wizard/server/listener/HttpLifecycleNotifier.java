package org.cobbzilla.wizard.server.listener;

import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.NameAndValue;
import org.cobbzilla.util.collection.SingletonList;
import org.cobbzilla.util.http.HttpRequestBean;
import org.cobbzilla.util.http.HttpResponseBean;
import org.cobbzilla.util.http.HttpUtil;
import org.cobbzilla.wizard.server.RestServer;
import org.cobbzilla.wizard.server.RestServerLifecycleEvent;
import org.cobbzilla.wizard.server.RestServerLifecycleListener;
import org.cobbzilla.wizard.server.config.HasHttpLifecycleNotifierConfiguration;
import org.cobbzilla.wizard.server.config.HttpLifecycleNotifierConfiguration;
import org.cobbzilla.wizard.server.config.RestServerConfiguration;

import java.util.List;

import static org.apache.http.HttpHeaders.CONTENT_TYPE;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.wizard.server.RestServerLifecycleEvent.*;

@Slf4j
public class HttpLifecycleNotifier<C extends RestServerConfiguration> implements RestServerLifecycleListener<C> {

    public static final SingletonList<NameAndValue> JSON_HEADER = new SingletonList<>(new NameAndValue(CONTENT_TYPE, APPLICATION_JSON));

    @Override public void beforeStart(RestServer<C> server) { notify(beforeStart, server); }
    @Override public void onStart    (RestServer<C> server) { notify(onStart, server); }
    @Override public void beforeStop (RestServer<C> server) { notify(beforeStop, server); }
    @Override public void onStop     (RestServer<C> server) { notify(onStop, server); }

    protected List<NameAndValue> getHeaders(RestServer<C> server) { return JSON_HEADER; }
    protected String getPayload(RestServerLifecycleEvent event, RestServer<C> server) { return event.name(); }

    protected void notify(RestServerLifecycleEvent event, RestServer<C> server) {
        final HasHttpLifecycleNotifierConfiguration config = (HasHttpLifecycleNotifierConfiguration) server.getConfiguration();
        final HttpLifecycleNotifierConfiguration notifierConfig = config.getHttpLifecycleNotifier();
        if (!notifierConfig.isConfigured()) return;

        if (notifierConfig.eventEnabled(event)) {
            final String payload = getPayload(event, server);
            if (payload != null) {
                final HttpResponseBean response;
                try {
                    response = HttpUtil.getResponse(new HttpRequestBean()
                            .setUri(notifierConfig.getUri())
                            .setMethod(notifierConfig.getMethod())
                            .setHeaders(getHeaders(server))
                            .setEntity(payload));
                } catch (Exception e) {
                    log.warn("notify(" + event + "): HTTP error: " + e);
                    return;
                }
                if (!response.isOk()) {
                    log.warn("notify(" + event + "): response not OK: " + response);
                }
            }
        }
    }

}
