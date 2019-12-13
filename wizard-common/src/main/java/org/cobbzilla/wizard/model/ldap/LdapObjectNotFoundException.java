package org.cobbzilla.wizard.model.ldap;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@NoArgsConstructor @AllArgsConstructor @Accessors(chain=true)
public class LdapObjectNotFoundException extends RuntimeException {

    @Getter @Setter private String dn;

}
