package org.cobbzilla.wizard.filters;

import lombok.*;

import static org.cobbzilla.util.time.TimeUtil.parseDuration;

@NoArgsConstructor @AllArgsConstructor @ToString(of={"limit", "interval", "block"})
public class ApiRateLimit {

    @Getter @Setter int limit;
    @Getter @Setter String interval;
    @Getter @Setter String block;

    public long getIntervalDuration () { return parseDuration(interval); }
    public long getBlockDuration () { return parseDuration(block); }

}
