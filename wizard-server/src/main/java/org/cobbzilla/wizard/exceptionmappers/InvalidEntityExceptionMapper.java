package org.cobbzilla.wizard.exceptionmappers;

import org.cobbzilla.wizard.validation.ConstraintViolationBean;
import org.cobbzilla.wizard.validation.InvalidEntityException;

import javax.validation.ConstraintViolation;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.List;

@Provider
public class InvalidEntityExceptionMapper
    extends AbstractConstraintViolationExceptionMapper<InvalidEntityException>
    implements ExceptionMapper<InvalidEntityException> {

    @Override public Response toResponse(InvalidEntityException exception) {
        return buildResponse(exception);
    }

    @Override protected List<ConstraintViolationBean> exception2json(InvalidEntityException exception) {
        final List<ConstraintViolation> violations = exception.getResult().getViolations();
        return getConstraintViolationBeans(violations);
    }

}
