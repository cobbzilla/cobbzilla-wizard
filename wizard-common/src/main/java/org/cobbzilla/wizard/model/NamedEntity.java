package org.cobbzilla.wizard.model;

import java.util.*;
import java.util.function.Function;
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

    static <E extends NamedEntity> Map<String, E> nameMap (Collection<E> things) {
        return empty(things)
                ? Collections.emptyMap()
                : things.stream().collect(Collectors.toMap(NamedEntity::getName, Function.identity()));
    }

}
