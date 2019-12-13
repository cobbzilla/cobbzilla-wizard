package org.cobbzilla.wizard.asset;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum AssetStorageType {

    local, s3, resource;

    @JsonCreator public static AssetStorageType create(String val) { return valueOf(val.toLowerCase()); }

}
