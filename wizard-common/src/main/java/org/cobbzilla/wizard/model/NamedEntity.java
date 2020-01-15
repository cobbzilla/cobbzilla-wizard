package org.cobbzilla.wizard.model;

import java.util.Comparator;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

public interface NamedEntity {

    int NAME_MAXLEN = 100;
    String[] NAME_FIELD_ARRAY = {"name"};

    String getName ();
    default boolean hasName () { return !empty(getName()); }

    Comparator<? extends NamedEntity> NAME_COMPARATOR
            = (Comparator<NamedEntity>) (o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName());

}
