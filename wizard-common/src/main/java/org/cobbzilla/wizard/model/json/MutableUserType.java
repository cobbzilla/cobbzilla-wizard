package org.cobbzilla.wizard.model.json;

import org.hibernate.HibernateException;
import org.hibernate.type.SerializationException;
import org.hibernate.usertype.UserType;

import java.io.Serializable;

/**
 * adapted from: https://github.com/pires/hibernate-postgres-jsonb
 *
 * A skeleton Hibernate {@link UserType}. Assumes, by default, that the return
 * type is mutable. Subtypes whose {@code deepCopy} implementation returns a
 * non-serializable object <strong>must override</strong>
 * {@link #disassemble(Object)}.
 */
public abstract class MutableUserType implements UserType {

    @Override public boolean isMutable() { return true; }

    @Override public boolean equals(Object x, Object y) throws HibernateException {
        if (x == y) return true;
        if ((x == null) || (y == null)) return false;
        return x.equals(y);
    }

    @Override public int hashCode(Object x) throws HibernateException {
        assert (x != null);
        return x.hashCode();
    }

    @Override public Object assemble(Serializable cached, Object owner)
            throws HibernateException {
        // also safe for mutable objects
        return deepCopy(cached);
    }

    /**
     * Disassembles the object in preparation for serialization.
     * See {@link org.hibernate.usertype.UserType#disassemble(java.lang.Object)}.
     * <p>
     * Expects {@link #deepCopy(Object)} to return a {@code Serializable}.
     * <strong>Subtypes whose {@code deepCopy} implementation returns a
     * non-serializable object must override this method.</strong>
     */
    @Override public Serializable disassemble(Object value) throws HibernateException {
        // also safe for mutable objects
        Object deepCopy = deepCopy(value);

        if (!(deepCopy instanceof Serializable)) {
            throw new SerializationException(
                    String.format("deepCopy of %s is not serializable", value), null);
        }

        return (Serializable) deepCopy;
    }

    @Override public Object replace(Object original, Object target, Object owner) throws HibernateException {
        // also safe for mutable objects
        return deepCopy(original);
    }
}
