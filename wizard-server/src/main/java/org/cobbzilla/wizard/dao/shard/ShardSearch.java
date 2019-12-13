package org.cobbzilla.wizard.dao.shard;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.wizard.util.ResultCollector;
import org.cobbzilla.wizard.util.ResultCollectorBase;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@NoArgsConstructor @AllArgsConstructor @Accessors(chain=true)
public class ShardSearch {

    @Getter @Setter protected String hash = null;
    public boolean hasHash() { return hash != null; }

    @Getter @Setter protected String hsql = null;
    @Getter @Setter protected List<Object> args = null;

    @Getter @Setter private ResultCollector collector = new ResultCollectorBase();
    @Getter @Setter private Comparator comparator = null;

    public ShardSearch (String hsql, List<Object> args) { this(hsql, args, null); }
    public ShardSearch (String hsql, List<Object> args, String hash) { this.hsql = hsql; this.args = args; this.hash = hash; }

    @Getter private int maxResults = Integer.MAX_VALUE;
    public ShardSearch setMaxResults (int max) {
        this.maxResults = max;
        collector.setMaxResults(max);
        return this;
    }

    @Getter @Setter private int maxResultsPerShard = Integer.MAX_VALUE;

    @Getter @Setter private Long timeout;
    public boolean hasTimeout() { return timeout != null && timeout > 0; }

    public <R> List<R> sort(List<R> results) {
        if (comparator != null) Collections.sort(results, getComparator());
        return results;
    }
}
