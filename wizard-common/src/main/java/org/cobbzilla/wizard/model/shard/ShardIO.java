package org.cobbzilla.wizard.model.shard;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ShardIO {

    read, write;

    @JsonCreator public static ShardIO create (String val) { return valueOf(val.toLowerCase()); }

}
