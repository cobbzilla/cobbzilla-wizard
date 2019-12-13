package org.cobbzilla.wizard.spring.config.rdbms;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.reflect.PoisonProxy;
import org.cobbzilla.wizard.model.crypto.EncryptedTypes;
import org.cobbzilla.wizard.server.config.*;
import org.cobbzilla.wizard.server.listener.DbPoolShutdownListener;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.ImprovedNamingStrategy;
import org.jasypt.encryption.pbe.PBEStringEncryptor;
import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.hibernate4.encryptor.HibernatePBEStringEncryptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.orm.hibernate4.HibernateTemplate;
import org.springframework.orm.hibernate4.HibernateTransactionManager;
import org.springframework.orm.hibernate4.LocalSessionFactoryBean;

import javax.sql.DataSource;
import java.beans.PropertyVetoException;
import java.util.Properties;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

@Slf4j
public class RdbmsConfigCommon {

    @SuppressWarnings("SpringJavaAutowiringInspection")
    @Autowired @Setter protected HasDatabaseConfiguration configuration;
    protected HasDatabaseConfiguration configuration () { return configuration; }

    public DataSource dataSource() {
        final DatabaseConfiguration dbConfiguration = getDatabase();
        final ComboPooledDataSource cpds = new ComboPooledDataSource();
        try {
            cpds.setDriverClass(dbConfiguration.getDriver());
        } catch (PropertyVetoException e) {
            return die("dataSource: "+e, e);
        }
        cpds.setJdbcUrl(dbConfiguration.getUrl());
        cpds.setUser(dbConfiguration.getUser());
        cpds.setPassword(dbConfiguration.getPassword());
        final DatabaseConnectionPoolConfiguration pool = dbConfiguration.getPool();
        if (pool.isEnabled()) {
            cpds.setIdentityToken(pool.getName());
            cpds.setDataSourceName(pool.getName());
            cpds.setInitialPoolSize(pool.getMin());
            cpds.setMinPoolSize(pool.getMin());
            cpds.setMaxPoolSize(pool.getMax());
            cpds.setAcquireIncrement(pool.getIncrement());
            if (pool.hasIdleTest()) cpds.setIdleConnectionTestPeriod(pool.getIdleTest());
            if (pool.hasRetryAttempts()) cpds.setAcquireRetryAttempts(pool.getRetryAttempts());
            if (pool.hasRetryDelay()) cpds.setAcquireRetryDelay(pool.getRetryDelay());
            ((RestServerConfiguration) configuration).getServer().addLifecycleListener(new DbPoolShutdownListener());
        }

        dbConfiguration.runPostDataSourceSetupHandlers();

        return cpds;
    }

    public HibernateTransactionManager transactionManager(SessionFactory sessionFactory) {
        final HibernateTransactionManager htm = new HibernateTransactionManager();
        htm.setSessionFactory(sessionFactory);
        return htm;
    }

    public HibernateTemplate hibernateTemplate(SessionFactory sessionFactory) {
        final HibernateTemplate hibernateTemplate = new HibernateTemplate(sessionFactory);
        return hibernateTemplate;
    }

    public LocalSessionFactoryBean sessionFactory() {
        final LocalSessionFactoryBean factory = new LocalSessionFactoryBean();
        factory.setNamingStrategy(ImprovedNamingStrategy.INSTANCE);
        factory.setDataSource(dataSource());
        factory.setHibernateProperties(hibernateProperties());
        factory.setPackagesToScan(getDatabase().getHibernate().getEntityPackages());
        return factory;
    }

    public Properties hibernateProperties() {
        final HibernateConfiguration hibernateConfiguration = getDatabase().getHibernate();
        final Properties properties = new Properties();
        properties.put("hibernate.dialect", hibernateConfiguration.getDialect());
        properties.put("hibernate.show_sql", hibernateConfiguration.isShowSql());
        properties.put("hibernate.hbm2ddl.auto", hibernateConfiguration.getHbm2ddlAuto());
        properties.put("hibernate.validator.apply_to_ddl", hibernateConfiguration.isApplyValidatorToDDL());
        properties.put("javax.persistence.verification.mode", hibernateConfiguration.getValidationMode());
        return properties;
    }

    public static final int MIN_KEY_LENGTH = 15;
    @Bean public PBEStringEncryptor strongEncryptor () {

        if (!getDatabase().isEncryptionEnabled()) {
            log.warn("strongEncryptor: encryption is disabled, will not work!");
            return PoisonProxy.wrap(PBEStringEncryptor.class);
        }

        final String key = getDatabase().getEncryptionKey();
        if (empty(key) || key.length() < MIN_KEY_LENGTH) die("strongEncryptor: encryption was enabled, but key was too short (min length "+MIN_KEY_LENGTH+"): '"+key+"'");

        final PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
        encryptor.setPassword(key);
        encryptor.setAlgorithm("PBEWithMD5AndTripleDES");
        encryptor.setPoolSize(getDatabase().getEncryptorPoolSize());
        return encryptor;
    }

    public DatabaseConfiguration getDatabase() { return configuration.getDatabase(); }

    @Bean public HibernatePBEStringEncryptor hibernateEncryptor() {
        final HibernatePBEStringEncryptor encryptor = new HibernatePBEStringEncryptor();
        encryptor.setEncryptor(strongEncryptor());
        encryptor.setRegisteredName(EncryptedTypes.STRING_ENCRYPTOR_NAME);
        return encryptor;
    }

    @Bean public HibernatePBEStringEncryptor hibernateIntegerEncryptor() {
        final HibernatePBEStringEncryptor encryptor = new HibernatePBEStringEncryptor();
        encryptor.setEncryptor(strongEncryptor());
        encryptor.setRegisteredName(EncryptedTypes.INTEGER_ENCRYPTOR_NAME);
        return encryptor;
    }

    @Bean public HibernatePBEStringEncryptor hibernateLongEncryptor() {
        final HibernatePBEStringEncryptor encryptor = new HibernatePBEStringEncryptor();
        encryptor.setEncryptor(strongEncryptor());
        encryptor.setRegisteredName(EncryptedTypes.LONG_ENCRYPTOR_NAME);
        return encryptor;
    }
}
