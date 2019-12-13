package org.cobbzilla.wizard.spring.config.rdbms;

import lombok.Getter;
import lombok.Setter;
import org.cobbzilla.wizard.server.config.DatabaseConfiguration;

public class RdbmsConfigSimple extends RdbmsConfigCommon {

    @Getter @Setter private DatabaseConfiguration database;

    public RdbmsConfigSimple(DatabaseConfiguration database) {
        this.database = database;
    }

}
