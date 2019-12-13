package org.cobbzilla.wizard.exceptionmappers;

import org.cobbzilla.wizard.validation.ConstraintViolationBean;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.ArrayList;
import java.util.List;

@Provider
public class ConstraintViolationExceptionMapper
        extends AbstractConstraintViolationExceptionMapper<ConstraintViolationException>
        implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        return buildResponse(exception);
    }

    protected List<ConstraintViolationBean> exception2json(ConstraintViolationException e) {
        final List<ConstraintViolationBean> violations = new ArrayList<>();
        for (ConstraintViolation violation : e.getConstraintViolations()) {
            violations.add(new ConstraintViolationBean(violation));
        }
        return violations;
    }

}
