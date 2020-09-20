package org.cobbzilla.wizard.spring.config.rdbms;

import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.server.config.DatabaseConfiguration;
import org.hibernate.SessionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.hibernate5.HibernateTemplate;
import org.springframework.orm.hibernate5.HibernateTransactionManager;
import org.springframework.orm.hibernate5.LocalSessionFactoryBean;

import javax.sql.DataSource;
import java.util.Properties;

@Configuration @Slf4j
public class RdbmsConfig extends RdbmsConfigCommon {

    @Bean public DatabaseConfiguration database() { return super.getDatabase(); }

    @Bean public DataSource dataSource() { return super.dataSource(); }

    @Bean public HibernateTransactionManager transactionManager(SessionFactory sessionFactory) {
        return super.transactionManager(sessionFactory);
    }

    @Bean public HibernateTemplate hibernateTemplate(SessionFactory sessionFactory) {
        return super.hibernateTemplate(sessionFactory);
    }

    @Bean public LocalSessionFactoryBean sessionFactory() { return super.sessionFactory(); }

    @Bean public Properties hibernateProperties() { return super.hibernateProperties(); }

}
