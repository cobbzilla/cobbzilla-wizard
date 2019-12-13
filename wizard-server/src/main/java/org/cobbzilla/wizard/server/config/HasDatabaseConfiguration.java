package org.cobbzilla.wizard.server.config;

import java.util.Map;

public interface HasDatabaseConfiguration {

    DatabaseConfiguration getDatabase();
    void setDatabase(DatabaseConfiguration config);

    Map<String, String> getEnvironment();

}
