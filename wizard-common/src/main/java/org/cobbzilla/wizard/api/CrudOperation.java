package org.cobbzilla.wizard.api;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum CrudOperation {

    create, read, update, delete;

    @JsonCreator public CrudOperation fromString (String s) { return valueOf(s.toLowerCase()); }

    public boolean isRead() { return this == read; }

}
