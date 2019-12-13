package org.cobbzilla.wizard.model.ldap;

public interface LdapDerivedValueProducer<E extends LdapEntity> {

    /**
     * Called for single-valued attributes
     * @param entity The entity to derive a value for
     * @return The value of the attribute
     */
    public String deriveValue(E entity);

    /**
     * Called for multi-valued attributes
     * @param entity The entity to derive a value for
     * @return The values of the attribute
     */
    public String[] deriveValues(E entity);

}
