package org.cobbzilla.wizard.model.search;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum SearchFieldType {

    string, integer, decimal, flag;

    @JsonCreator public static SearchFieldType fromString (String val) { return valueOf(val.toLowerCase()); }

}
