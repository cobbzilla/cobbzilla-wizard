package org.cobbzilla.wizard.model.ldap;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@NoArgsConstructor @AllArgsConstructor @Accessors(chain=true)
public class LdapAttributeDelta {

    @Getter @Setter private LdapAttribute attribute;
    @Getter @Setter private LdapOperation operation;

    @Override public String toString() { return operation.name() + "(" + attribute + ")"; }

}
