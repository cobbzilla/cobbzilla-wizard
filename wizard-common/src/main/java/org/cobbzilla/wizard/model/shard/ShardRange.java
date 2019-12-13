package org.cobbzilla.wizard.model.shard;

import lombok.*;

import javax.persistence.Embeddable;

@Embeddable @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(of={"logicalStart", "logicalEnd"})
public class ShardRange implements Comparable<ShardRange> {

    @Getter @Setter private int logicalStart;
    @Getter @Setter private int logicalEnd;

    public boolean mapsShard(int shard) { return logicalStart <= shard && logicalEnd > shard; }

    @Override public int compareTo(ShardRange o) {
        if (logicalStart == o.logicalStart && logicalEnd == o.logicalEnd) return 0;
        if (logicalStart == o.logicalStart) {
            return o.logicalEnd - logicalEnd;
        }
        return o.logicalStart - logicalStart;
    }

}
