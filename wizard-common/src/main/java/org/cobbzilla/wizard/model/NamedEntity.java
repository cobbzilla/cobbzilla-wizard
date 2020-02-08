package org.cobbzilla.wizard.model;

import java.util.Collection;
import java.util.Comparator;
import java.util.stream.Collectors;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

public interface NamedEntity {

    int NAME_MAXLEN = 100;
    String[] NAME_FIELD_ARRAY = {"name"};

    String getName ();
    default boolean hasName () { return !empty(getName()); }

    Comparator<? extends NamedEntity> NAME_COMPARATOR
            = (Comparator<NamedEntity>) (o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName());

    static String names (Collection<? extends NamedEntity> c) {
        return empty(c) ? "(empty)" : c.stream().map(NamedEntity::getName).collect(Collectors.joining(", "));
    }

}
