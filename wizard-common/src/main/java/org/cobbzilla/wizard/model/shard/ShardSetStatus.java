package org.cobbzilla.wizard.model.shard;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

@NoArgsConstructor @AllArgsConstructor @Accessors(chain=true)
public class ShardSetStatus {

    @Getter @Setter private String name;

    @Getter @Setter private List<? extends ShardMap> readShards;
    @Getter @Setter private boolean readValid;

    @Getter @Setter private List<? extends ShardMap> writeShards;
    @Getter @Setter private boolean writeValid;

    @JsonIgnore public boolean isValid() { return readValid && writeValid; }

}
