package org.cobbzilla.wizard.model.search;

import org.jasypt.hibernate4.encryptor.HibernatePBEStringEncryptor;

public interface SqlViewFieldSetter {

    void set(Object target, String entityProperty, Object value, HibernatePBEStringEncryptor hibernateEncryptor);

}
