package org.cobbzilla.wizard.filters.auth;

import java.security.Principal;

public interface TokenPrincipal extends Principal {

    String getApiToken ();
    void setApiToken (String token);

}
