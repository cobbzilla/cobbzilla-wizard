package org.cobbzilla.wizard.asset;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

public class AssetStorageConfiguration {

    @Getter @Setter private AssetStorageType type = AssetStorageType.local;
    @Getter @Setter private Map<String, String> config;

}
