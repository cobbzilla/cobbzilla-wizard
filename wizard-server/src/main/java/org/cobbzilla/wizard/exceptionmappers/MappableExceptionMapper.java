package org.cobbzilla.wizard.exceptionmappers;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.ConnectionClosedException;
import org.glassfish.jersey.server.internal.process.MappableException;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import static org.cobbzilla.wizard.server.RestServerBase.reportError;

@Provider @Slf4j
public class MappableExceptionMapper implements ExceptionMapper<MappableException> {

    @Override public Response toResponse(MappableException exception) {
        reportError(exception);
        if (exception.getCause() instanceof ConnectionClosedException) {
            log.warn("connection was closed");
        }
        return Response.serverError().build();
    }

}
