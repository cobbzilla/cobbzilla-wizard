package org.cobbzilla.wizard.filters.auth;

public interface AuthProvider<T> {

    T find(String token);

}
