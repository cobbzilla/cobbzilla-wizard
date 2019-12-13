package org.cobbzilla.wizard.filters.auth;

public interface UserSessionTokenPrincipal extends TokenPrincipal {

    String getSubUser();
    void setSubUser(String userSessionToken);

}
