package org.cobbzilla.wizard.model;

import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.jdbc.TypedResultSetBean;
import org.cobbzilla.util.reflect.ReflectionUtil;
import org.hibernate.annotations.Type;
import org.jasypt.hibernate4.encryptor.HibernatePBEStringEncryptor;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.cobbzilla.util.reflect.ReflectionUtil.callFactoryMethod;
import static org.cobbzilla.util.reflect.ReflectionUtil.getDeclaredField;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.*;

@Slf4j
public class RSBean<T extends Identifiable> extends TypedResultSetBean<T> {

    private HibernatePBEStringEncryptor stringEncryptor;
    private HibernatePBEStringEncryptor longEncryptor;

    public RSBean(Class<T> clazz, HibernatePBEStringEncryptor stringEncryptor, HibernatePBEStringEncryptor longEncryptor, ResultSet rs) throws SQLException {
        super(clazz, rs);
        init(stringEncryptor, longEncryptor);
    }
    public RSBean(Class<T> clazz, HibernatePBEStringEncryptor stringEncryptor, HibernatePBEStringEncryptor longEncryptor, PreparedStatement ps) throws SQLException {
        super(clazz, ps);
        init(stringEncryptor, longEncryptor);
    }
    public RSBean(Class<T> clazz, HibernatePBEStringEncryptor stringEncryptor, HibernatePBEStringEncryptor longEncryptor, Connection conn, String sql) throws SQLException {
        super(clazz, conn, sql);
        init(stringEncryptor, longEncryptor);
    }
    private void init(HibernatePBEStringEncryptor stringEncryptor, HibernatePBEStringEncryptor longEncryptor) {
        this.stringEncryptor = stringEncryptor;
        this.longEncryptor = longEncryptor;
    }

    @Override protected void readField(T thing, String field, Object value) {
        final Field declaredField = getDeclaredField(thing.getClass(), field);
        if (declaredField == null) {
            log.info("set: field "+field+" does not exist in "+thing.getClass().getSimpleName()+", trying setter");
            super.readField(thing, field, value);
            return;
        }
        final Class<?> fieldType = declaredField.getType();
        if (fieldType.isEnum()) {
            value = callFactoryMethod(fieldType, value);
            if (value !=  null) ReflectionUtil.set(thing, field, value);
            return;
        }
        final Type type = declaredField.getAnnotation(Type.class);
        if (type != null) {
            switch (type.type()) {
                case ENCRYPTED_STRING:
                    if (value != null) ReflectionUtil.set(thing, field, stringEncryptor.decrypt(value.toString()));
                    return;
                case ENCRYPTED_LONG:
                    if (value != null) ReflectionUtil.set(thing, field, Long.valueOf(longEncryptor.decrypt(value.toString())));
                    return;
                case ENCRYPTED_INTEGER:
                    if (value != null) ReflectionUtil.set(thing, field, Integer.valueOf(longEncryptor.decrypt(value.toString())));
                    return;
            }
        }
        super.readField(thing, field, value);
    }

}
