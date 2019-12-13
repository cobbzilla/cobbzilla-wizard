package org.cobbzilla.wizard.exceptionmappers;

import org.cobbzilla.wizard.resources.ResourceHttpException;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import static org.cobbzilla.wizard.resources.ResourceUtil.status;
import static org.cobbzilla.wizard.server.RestServerBase.reportError;

@Provider
public class HttpResponseExceptionMapper implements ExceptionMapper<ResourceHttpException> {

    @Override public Response toResponse(ResourceHttpException e) {
        if (e.getStatusClass() == 5) reportError(e);
        return status(e.getStatus(), e.getEntity());
    }

}
