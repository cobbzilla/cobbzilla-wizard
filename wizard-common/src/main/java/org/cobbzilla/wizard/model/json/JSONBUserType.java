package org.cobbzilla.wizard.model.json;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.ParameterizedType;
import org.hibernate.usertype.UserType;
import org.postgresql.util.PGobject;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Properties;

import static org.cobbzilla.util.json.JsonUtil.fromJsonOrDie;
import static org.cobbzilla.util.json.JsonUtil.toJsonOrDie;
import static org.cobbzilla.util.reflect.ReflectionUtil.forName;

/**
 * adapted from: https://github.com/pires/hibernate-postgres-jsonb
 *
 * A {@link UserType} that persists objects as JSONB.
 * <p>
 * Unlike the default JPA object mapping, {@code JSONBUserType} can also be used
 * for properties that do not implement {@link Serializable}.
 * <p>
 * Users intending to use this type for mutable non-<code>Collection</code>
 * objects should override {@link #deepCopyValue(Object)} to correctly return a
 * <u>copy</u> of the object.
 */
public class JSONBUserType extends CollectionUserType implements ParameterizedType {

    public static final String JSONB_TYPE = "jsonb";
    public static final String PARAM_CLASS = "CLASS";

    private Class returnedClass;

    @Override public Class returnedClass() { return Object.class; }

    @Override public int[] sqlTypes() { return new int[]{Types.JAVA_OBJECT}; }

    @Override public Object nullSafeGet (ResultSet resultSet, String[] names,
                                         SharedSessionContractImplementor sharedSessionContractImplementor,
                                         Object o)
            throws HibernateException, SQLException {
        final String json = resultSet.getString(names[0]);
        return json == null ? null : fromJsonOrDie(json, returnedClass);
    }

    @Override public void nullSafeSet (PreparedStatement st, Object value, int index,
                                       SharedSessionContractImplementor sharedSessionContractImplementor)
            throws HibernateException, SQLException {
        final String json = value == null ? null : toJsonOrDie(value);
        final PGobject pgo = new PGobject();
        pgo.setType(JSONB_TYPE);
        pgo.setValue(json);
        st.setObject(index, pgo);
    }

    @Override protected Object deepCopyValue(Object value) { return value; }

    @Override public void setParameterValues(Properties parameters) {
        returnedClass = forName(parameters.getProperty(PARAM_CLASS));
    }

}
