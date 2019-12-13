package org.cobbzilla.wizard.model.ldap;

import lombok.*;
import lombok.experimental.Accessors;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

@NoArgsConstructor @AllArgsConstructor @Accessors(chain=true) @EqualsAndHashCode(of={"name", "value"})
public class LdapAttribute {

    @Getter @Setter private String name;
    @Getter @Setter private String value;

    public LdapAttribute(String name) { this.name = name; }

    public boolean hasValue() { return !empty(value); }

    public boolean isName(String name) { return this.name.equalsIgnoreCase(name); }

    @Override public String toString() { return hasValue() ? name + ": " + value : name; }

}
