package org.cobbzilla.wizard.server.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang.ArrayUtils;
import org.cobbzilla.util.http.HttpMethods;
import org.cobbzilla.wizard.server.RestServerLifecycleEvent;

import java.util.HashMap;
import java.util.Map;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

public class HttpLifecycleNotifierConfiguration {

    @Getter @Setter private String uri;
    @Getter @Setter private String method = HttpMethods.POST;
    @Getter @Setter private RestServerLifecycleEvent[] events;
    @Getter @Setter private Map<String, String> properties = new HashMap<>();

    @JsonIgnore public boolean isConfigured() { return !empty(uri) && !empty(events); }

    public boolean eventEnabled(RestServerLifecycleEvent event) { return !empty(events) && ArrayUtils.contains(events, event); }

}
