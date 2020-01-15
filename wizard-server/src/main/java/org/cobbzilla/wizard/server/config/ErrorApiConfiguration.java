package org.cobbzilla.wizard.server.config;

import airbrake.AirbrakeNoticeBuilder;
import airbrake.AirbrakeNotifier;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.buffer.CircularFifoBuffer;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.system.CommandShell.hostname;

@NoArgsConstructor @AllArgsConstructor @Slf4j @ToString
public class ErrorApiConfiguration {

    @Getter @Setter private String url;
    @Getter @Setter private String key;
    @Setter private String env;
    @Getter @Setter private int dupCacheSize = 100;
    @Getter @Setter private int bufferSize = 200;
    @Getter @Setter private long sendInterval = SECONDS.toMillis(5);

    @Getter(lazy=true) private final AirbrakeNotifier notifier = initNotifier();

    private AirbrakeNotifier initNotifier() { return new AirbrakeNotifier(getUrl()); }

    public String getEnv() { return !empty(env) ? env : hostname(); }

    public boolean isValid() { return !empty(getUrl()) && !empty(getKey()) && !empty(getEnv()); }

    private final CircularFifoBuffer cache = new CircularFifoBuffer(dupCacheSize);

    public void report(Exception e) {
        if (inCache(e)) return;
        final AirbrakeNoticeBuilder builder = new AirbrakeNoticeBuilder(getKey(), e, getEnv());
        final int responseCode = getNotifier().notify(builder.newNotice());
        if (responseCode != 200) log.warn("report("+e+"): notifier API returned "+responseCode);
    }

    public void report(String s) {
        if (inCache(s)) return;
        final AirbrakeNoticeBuilder builder = new AirbrakeNoticeBuilder(getKey(), s, getEnv());
        final int responseCode = getNotifier().notify(builder.newNotice());
        if (responseCode != 200) log.warn("report("+s+"): notifier API returned "+responseCode);
    }

    public boolean inCache(Object o) {
        synchronized (cache) {
            if (cache.contains(o)) {
                log.warn("inCache(" + o + "): already reported recently");
                return true;
            }
            cache.add(o);
        }
        return false;
    }

}
