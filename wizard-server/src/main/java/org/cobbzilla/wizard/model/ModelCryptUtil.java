package org.cobbzilla.wizard.model;

import org.cobbzilla.wizard.server.config.HasDatabaseConfiguration;
import org.cobbzilla.wizard.spring.config.rdbms.RdbmsConfig;
import org.jasypt.hibernate4.encryptor.HibernatePBEStringEncryptor;

public class ModelCryptUtil {

    public static HibernatePBEStringEncryptor getCryptor(HasDatabaseConfiguration dbConfig) {
        final RdbmsConfig config = new RdbmsConfig();
        config.setConfiguration(dbConfig);
        return config.hibernateEncryptor();
    }

}
