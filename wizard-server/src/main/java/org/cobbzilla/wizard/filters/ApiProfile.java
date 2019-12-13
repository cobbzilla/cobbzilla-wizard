package org.cobbzilla.wizard.filters;

public interface ApiProfile {
    default boolean isAdmin() { return false; }
}
