package org.cobbzilla.wizard.exceptionmappers;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class UnknownExceptionMapper implements ExceptionMapper<Throwable> {

    @Override public Response toResponse(Throwable t) {
        t.printStackTrace();
        return Response.serverError().entity(t.getMessage()).build();
    }

}
