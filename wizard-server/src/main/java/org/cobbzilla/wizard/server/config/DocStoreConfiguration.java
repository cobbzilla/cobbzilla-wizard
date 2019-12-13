package org.cobbzilla.wizard.server.config;

import lombok.Getter;
import lombok.Setter;

public class DocStoreConfiguration {

    @Getter @Setter private String host;
    @Getter @Setter private int port;
    @Getter @Setter private String dbName;
    @Getter @Setter private String[] entityPackages;

}
