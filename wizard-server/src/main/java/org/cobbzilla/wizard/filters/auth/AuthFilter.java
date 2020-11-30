package org.cobbzilla.wizard.filters.auth;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import java.io.IOException;
import java.util.Set;

import static org.cobbzilla.wizard.resources.ResourceUtil.unauthorized;

public abstract class AuthFilter<T extends TokenPrincipal> implements ContainerRequestFilter {

    public abstract String getAuthTokenHeader();
    protected String getSubUserHeader() { return null; }
    protected abstract Set<String> getSkipAuthPaths();
    protected abstract Set<String> getSkipAuthPrefixes();

    @Override
    public void filter(ContainerRequestContext request) throws IOException {

        final String u = request.getUriInfo().getPath();
        final String uri = u.startsWith("/") ? u : "/" + u; // ensure there is always a leading / for filtering purposes
        boolean canSkip = canSkip(uri);

        final String token = request.getHeaderString(getAuthTokenHeader());
        if (token == null) {
            if (!canSkip) request.abortWith(unauthorized());
            return;
        }

        final T principal = getAuthProvider().find(token);
        if (principal == null) {
            if (!canSkip) request.abortWith(unauthorized());
            return;
        }

        if (!isPermitted(principal, request)) {
            request.abortWith(unauthorized());
            return;
        }

        principal.setApiToken(token);
        request.setSecurityContext(getSecurityContext(request, principal));
    }

    protected boolean canSkip(String uri) {
        return getSkipAuthPaths().contains(uri) || startsWith(uri, getSkipAuthPrefixes());
    }

    protected SimpleSecurityContext getSecurityContext(ContainerRequestContext request, T principal) {
        final String subUserHeader = getSubUserHeader();
        if (subUserHeader != null) {
            final String subUserId = request.getHeaderString(subUserHeader);
            if (subUserId != null) {
                if (principal instanceof UserSessionTokenPrincipal) {
                    ((UserSessionTokenPrincipal)principal).setSubUser(subUserId);
                }
            }
        }
        return new SimpleSecurityContext(principal);
    }

    protected boolean startsWith(String uri, Set<String> prefixes) {
        for (String path : prefixes) {
            if (uri.startsWith(path)) return true;
        }
        return false;
    }

    protected abstract boolean isPermitted(T principal, ContainerRequestContext request);

    protected abstract AuthProvider<T> getAuthProvider();

}
