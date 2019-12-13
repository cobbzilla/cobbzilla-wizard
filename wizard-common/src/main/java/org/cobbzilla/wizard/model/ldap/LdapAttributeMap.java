package org.cobbzilla.wizard.model.ldap;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import lombok.Getter;

import java.util.*;

public class LdapAttributeMap {

    @JsonIgnore private Map<String, LdapAttributeType> typesByJavaName = new HashMap<>();
    @JsonIgnore private Map<String, LdapAttributeType> typesByLdapName = new HashMap<>();
    @JsonIgnore protected Multimap<String, LdapAttribute> attributesByJavaName = LinkedListMultimap.create();
    @JsonIgnore protected Multimap<String, LdapAttribute> attributesByLdapName = LinkedListMultimap.create();
    @JsonIgnore @Getter private List<LdapAttributeDelta> deltas = new ArrayList<>();
    @Getter @JsonIgnore private Set<LdapAttributeType> derivedTypes= new HashSet<>();

    public void addType (LdapAttributeType type) {
        typesByJavaName.put(type.getJavaName(), type);
        typesByLdapName.put(type.getLdapName(), type);
        if (type.isDerived()) derivedTypes.add(type);
    }

    public void addAttribute (LdapAttribute attribute) {
        final String name = attribute.getName();
        final LdapAttributeType type = typesByLdapName.get(name);
        attributesByJavaName.put(type == null ? name : type.getJavaName(), attribute);
        attributesByLdapName.put(type == null ? name : type.getLdapName(), attribute);
        deltas.add(new LdapAttributeDelta(attribute, LdapOperation.add));
    }

    public void replaceAttribute(LdapAttribute attribute) {
        final String name = attribute.getName();
        final LdapAttributeType type = typesByLdapName.get(name);
        String javaName = type == null ? name : type.getJavaName();
        String ldapName = type == null ? name : type.getLdapName();
        attributesByJavaName.removeAll(javaName);
        attributesByLdapName.removeAll(ldapName);
        attributesByJavaName.put(javaName, attribute);
        attributesByLdapName.put(ldapName, attribute);
        deltas.add(new LdapAttributeDelta(attribute, LdapOperation.replace));
    }

    public void removeAttribute (String name) {
        final LdapAttributeType type = typesByLdapName.get(name);
        final String javaName = type == null ? name : type.getJavaName();
        final String ldapName = type == null ? name : type.getLdapName();
        final LdapAttribute attribute = new LdapAttribute(name);
        boolean found = !attributesByJavaName.removeAll(javaName).isEmpty();
        found = found || !attributesByLdapName.removeAll(ldapName).isEmpty();
        if (found) deltas.add(new LdapAttributeDelta(attribute, LdapOperation.delete));
    }

    public void removeAttribute (String name, String value) {
        final LdapAttributeType type = typesByLdapName.get(name);
        final String javaName = type == null ? name : type.getJavaName();
        final String ldapName = type == null ? name : type.getLdapName();
        final LdapAttribute attribute = new LdapAttribute(name, value);
        boolean found = attributesByJavaName.remove(javaName, attribute);
        found = found || attributesByLdapName.remove(ldapName, attribute);
        if (found) deltas.add(new LdapAttributeDelta(attribute, LdapOperation.delete));
    }

    public List<LdapAttribute> attributes () { return getAttributes(); }

    public List<LdapAttribute> getAttributes () {
        return new ArrayList<>(attributesByJavaName.values());
    }

    public void clean() { deltas.clear(); }
    public boolean isDirty() { return !deltas.isEmpty(); }

    public boolean contains(String attrName) { return attributesByLdapName.containsKey(attrName); }

    public LdapAttributeType getType(String attrName) { return typesByLdapName.get(attrName); }

}
