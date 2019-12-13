package org.cobbzilla.wizard.model.ldap;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.notSupported;

@AllArgsConstructor @Accessors(chain=true) @EqualsAndHashCode(of="javaName")
public class LdapAttributeType {

    public static final String OBJECT_CLASS = "objectClass";

    public static final LdapAttributeType objectClass = new LdapAttributeType(OBJECT_CLASS, OBJECT_CLASS, new LdapDerivedValueProducer() {
        @Override public String deriveValue(LdapEntity entity) { return notSupported(); }
        @Override public String[] deriveValues(LdapEntity entity) { return entity.getObjectClasses(); }
    }, true);

    @Getter @Setter private String javaName;
    @Getter @Setter private String ldapName;
    @Getter @Setter private LdapDerivedValueProducer derived = null;
    @Getter @Setter private boolean multiple = false;

    public static LdapAttributeType standardAttr(String javaName, String ldapName) {
        return new LdapAttributeType(javaName, ldapName, null, false);
    }
    public static LdapAttributeType multipleAttr(String javaName, String ldapName) {
        return new LdapAttributeType(javaName, ldapName, null, true);
    }
    public static LdapAttributeType derivedAttr(String javaName, String ldapName, LdapDerivedValueProducer derived) {
        return new LdapAttributeType(javaName, ldapName, derived, false);
    }

    public String toString() {
        return "java:" + javaName + "/ldap:" + ldapName + "/multiple:" + multiple
                + (derived == null ? "" : "/derived:"+derived.getClass().getSimpleName());
    }

    public boolean isDerived() { return derived != null; }

    public String getValue(LdapEntity ldapEntity) {
        if (!isDerived()) return die("Not a derived attribute: "+this);
        if (isMultiple()) return die("Derived attribute is multi-valued: "+this);
        return derived.deriveValue(ldapEntity);
    }

    public String[] getValues(LdapEntity ldapEntity) {
        if (!isDerived()) return die("Not a derived attribute: "+this);
        if (!isMultiple()) return die("Derived attribute is single-valued: "+this);
        return derived.deriveValues(ldapEntity);
    }
}
