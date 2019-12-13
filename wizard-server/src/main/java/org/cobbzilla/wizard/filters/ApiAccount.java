package org.cobbzilla.wizard.filters;

public interface ApiAccount {
    default boolean isAdmin() { return false; }
}
