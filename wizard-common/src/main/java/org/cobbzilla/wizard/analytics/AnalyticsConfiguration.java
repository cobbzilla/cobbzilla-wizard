package org.cobbzilla.wizard.analytics;

import lombok.Getter;
import lombok.Setter;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

public class AnalyticsConfiguration {

    public static final int DEFAULT_PORT = 443;
    public static final Long DEFAULT_REPORT_INTERVAL = SECONDS.toMillis(5);

    @Getter @Setter private String handler;

    @Getter @Setter private String host;
    @Setter private Integer port;
    public Integer getPort () { return port == null ? DEFAULT_PORT : port; }

    @Getter @Setter private String username;
    @Getter @Setter private String password;
    @Getter @Setter private String env;

    @Setter private Long reportInterval;
    public Long getReportInterval () { return reportInterval == null ? DEFAULT_REPORT_INTERVAL : reportInterval; }

    public boolean valid () {
        return !empty(handler) && !empty(host) && !empty(username) && !empty(password) && !empty(env);
    }
}
