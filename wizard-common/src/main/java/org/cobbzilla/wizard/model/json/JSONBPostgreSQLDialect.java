package org.cobbzilla.wizard.model.json;

import org.hibernate.dialect.PostgreSQL9Dialect;

import java.sql.Types;

/**
 * adapted from: https://github.com/pires/hibernate-postgres-jsonb
 */
public class JSONBPostgreSQLDialect extends PostgreSQL9Dialect {

    public JSONBPostgreSQLDialect() {
        super();
        registerColumnType(Types.JAVA_OBJECT, JSONBUserType.JSONB_TYPE);
    }

}
