package org.cobbzilla.wizard.ldap;

import org.cobbzilla.wizard.model.ldap.LdapDerivedValueProducer;
import org.cobbzilla.wizard.model.ldap.LdapEntity;

import static org.cobbzilla.util.daemon.ZillaRuntime.notSupported;
import static org.cobbzilla.wizard.ldap.LdapUtil.getFirstDnValue;

public class FirstDnPartValueProducer implements LdapDerivedValueProducer {

    public static FirstDnPartValueProducer instance = new FirstDnPartValueProducer();

    @Override public String deriveValue(LdapEntity entity) {
        return getFirstDnValue(entity.getDn());
    }

    @Override public String[] deriveValues(LdapEntity entity) {
        return notSupported();
    }

}
