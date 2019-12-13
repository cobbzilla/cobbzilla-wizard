package org.cobbzilla.wizard.model.ldap;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum LdapOperation {

    add, replace, delete;

    @JsonCreator public static LdapOperation create (String val) { return valueOf(val.toLowerCase()); }

}
