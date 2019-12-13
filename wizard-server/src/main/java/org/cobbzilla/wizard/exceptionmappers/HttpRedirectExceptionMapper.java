package org.cobbzilla.wizard.exceptionmappers;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import static org.cobbzilla.wizard.resources.ResourceUtil.redirect;

@Provider
public class HttpRedirectExceptionMapper implements ExceptionMapper<HttpRedirectException> {

    @Override public Response toResponse(HttpRedirectException e) {
        return redirect(e.getStatus(), e.getLocation());
    }

}
