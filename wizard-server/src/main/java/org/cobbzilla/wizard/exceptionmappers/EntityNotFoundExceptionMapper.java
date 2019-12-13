package org.cobbzilla.wizard.exceptionmappers;

import javax.persistence.EntityNotFoundException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import static org.cobbzilla.wizard.resources.ResourceUtil.notFound;

@Provider
public class EntityNotFoundExceptionMapper
        extends AbstractConstraintViolationExceptionMapper<EntityNotFoundException>
        implements ExceptionMapper<EntityNotFoundException> {

    @Override public Response toResponse(EntityNotFoundException e) { return notFound(e.getMessage()); }

}
