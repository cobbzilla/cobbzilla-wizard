package org.cobbzilla.wizard.server.config;

import lombok.Getter;
import lombok.Setter;

import static org.cobbzilla.util.system.Bytes.KB;

public class ShardSetConfiguration {

    @Getter @Setter private String name;
    @Getter @Setter private String entity;

    // default is 64k logical shards
    public static final int DEFAULT_LOGICAL_SHARDS = (int) (64 * KB);

    @Getter @Setter private int logicalShards = DEFAULT_LOGICAL_SHARDS;

}
