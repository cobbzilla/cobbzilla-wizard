package org.cobbzilla.wizard.server.config;

import lombok.*;
import lombok.extern.slf4j.Slf4j;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.system.CommandShell.hostname;

@NoArgsConstructor @AllArgsConstructor @Slf4j @ToString(of={"url", "env"})
public class ErrorApiConfiguration {

    @Getter @Setter private String url;
    @Getter @Setter private String key;

    @Setter private String env;
    public String getEnv() { return !empty(env) ? env : hostname(); }

    @Getter @Setter private int dupCacheSize = 100;
    @Getter @Setter private int bufferSize = 200;
    @Getter @Setter private long sendInterval = SECONDS.toMillis(5);

    public boolean isValid() { return !empty(getUrl()) && !empty(getKey()) && !empty(getEnv()); }

}
