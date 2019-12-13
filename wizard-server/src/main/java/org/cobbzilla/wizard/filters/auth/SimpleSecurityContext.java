package org.cobbzilla.wizard.filters.auth;

import lombok.AllArgsConstructor;

import javax.ws.rs.core.SecurityContext;
import java.security.Principal;

@AllArgsConstructor
public class SimpleSecurityContext implements SecurityContext {

    private Principal principal;

    @Override public Principal getUserPrincipal() { return principal; }
    @Override public boolean isUserInRole(String s) { return false; }
    @Override public boolean isSecure() { return false; }
    @Override public String getAuthenticationScheme() { return null; }

}
