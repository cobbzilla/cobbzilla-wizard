package org.cobbzilla.wizard.dao.sql;

public interface SQLFieldTransformer {

    Object sqlToObject(Object object, Object input);

}
