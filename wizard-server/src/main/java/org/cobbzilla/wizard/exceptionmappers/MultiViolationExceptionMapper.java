package org.cobbzilla.wizard.exceptionmappers;

import org.cobbzilla.util.http.HttpStatusCodes;
import org.cobbzilla.wizard.validation.MultiViolationException;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import static org.cobbzilla.wizard.resources.ResourceUtil.status;

@Provider
public class MultiViolationExceptionMapper implements ExceptionMapper<MultiViolationException> {

    @Override public Response toResponse(MultiViolationException e) {
        return status(HttpStatusCodes.UNPROCESSABLE_ENTITY, e.getViolations());
    }

}
