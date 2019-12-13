package org.cobbzilla.wizard.server.config;

import airbrake.AirbrakeNoticeBuilder;
import airbrake.AirbrakeNotifier;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.buffer.CircularFifoBuffer;
import org.cobbzilla.util.system.CommandShell;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

@NoArgsConstructor @AllArgsConstructor @Slf4j @ToString
public class ErrorApiConfiguration {

    @Getter @Setter private String url;
    @Getter @Setter private String key;
    @Setter private String env;

    @Getter(lazy=true) private final AirbrakeNotifier notifier = initNotifier();
    private AirbrakeNotifier initNotifier() { return new AirbrakeNotifier(getUrl()); }

    public String getEnv() { return !empty(env) ? env : CommandShell.hostname(); }

    public boolean isValid() { return !empty(getUrl()) && !empty(getKey()) && !empty(getEnv()); }

    private final CircularFifoBuffer cache = new CircularFifoBuffer(20);

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
