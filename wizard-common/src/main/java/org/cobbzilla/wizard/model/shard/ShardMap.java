package org.cobbzilla.wizard.model.shard;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.wizard.model.IdentifiableBase;

import javax.persistence.Embedded;
import javax.persistence.MappedSuperclass;
import javax.persistence.Transient;
import javax.validation.Valid;
import javax.validation.constraints.Size;
import java.util.Comparator;

@MappedSuperclass @Accessors(chain=true) @EqualsAndHashCode(of={"shardSet","url","range"}, callSuper=false)
public class ShardMap extends IdentifiableBase {

    @Size(max=1024, message="err.shardSet.length")
    @Getter @Setter private String shardSet;

    @Valid
    @Embedded @Getter @Setter private ShardRange range;

    @Size(max=1024, message="err.url.length")
    @Getter @Setter private String url;

    @Getter @Setter private boolean allowRead;
    @Getter @Setter private boolean allowWrite;

    public boolean mapsShard(int shard) { return range.mapsShard(shard); }

    public boolean isSameRange (ShardMap other) { return range.equals(other.getRange()); }

    public static final Comparator<ShardMap> RANGE_COMPARATOR = new Comparator<ShardMap>() {
        @Override public int compare(ShardMap m1, ShardMap m2) {
            return m2.getRange().compareTo(m1.getRange());
        }
    };

    @Override public String toString() {
        return "ShardMap{" + shardSet
                + '/' + range.getLogicalStart() + "-" + range.getLogicalEnd()
                + '/' + (allowRead && allowWrite ? "read+write" : allowRead ? "read" : allowWrite ? "write" : "disabled")
                + '/' + url + '}';
    }

    @Transient @Getter @Setter private boolean defaultShard = false;

    @Transient @JsonIgnore public String getDbName() { return url == null || url.indexOf('/') == -1 ? url : url.substring(url.indexOf('/')+1); }

}
