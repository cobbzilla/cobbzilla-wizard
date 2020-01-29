package org.cobbzilla.wizard.model.search;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum SortOrder {

    ASC, DESC;

    public static final String DEFAULT_SORT = DESC.name();

    @JsonCreator public static SortOrder fromString(String val) { return valueOf(val.toUpperCase()); }

    public boolean isAscending () { return this == ASC; }
    public boolean isDescending () { return this == DESC; }

}
