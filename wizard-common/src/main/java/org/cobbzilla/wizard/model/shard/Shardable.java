package org.cobbzilla.wizard.model.shard;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.cobbzilla.wizard.model.Identifiable;

public interface Shardable extends Identifiable {

    @JsonIgnore String getHashToShardField();

}
