package org.cobbzilla.wizard.server.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.cobbzilla.util.daemon.ZillaRuntime.hexnow;

@ToString
public class DatabaseConnectionPoolConfiguration {

    @Getter @Setter private String name = "Pool-"+hexnow()+"-"+randomAlphanumeric(10);
    @Getter @Setter private boolean enabled = false;

    protected int getDefaultMin() { return 5; }
    protected int getDefaultMax() { return 100; }
    protected int getDefaultIncrement() { return 5; }

    @Setter private Integer min;
    public Integer getMin() { return min != null ? min : getDefaultMin(); }

    @Setter private Integer max;
    public Integer getMax() { return max != null ? max : getDefaultMin(); }

    @Setter private Integer increment;
    public Integer getIncrement() { return increment != null ? increment : getDefaultMin(); }

    @Getter @Setter private Integer idleTest;
    public boolean hasIdleTest () { return idleTest != null; }

    @Getter @Setter private Integer retryAttempts;
    public boolean hasRetryAttempts() { return retryAttempts != null; }

    @Getter @Setter private Integer retryDelay;
    public boolean hasRetryDelay() { return retryDelay != null; }

}
