package org.cobbzilla.wizard.exceptionmappers;

import org.cobbzilla.wizard.validation.ConstraintViolationBean;
import org.cobbzilla.wizard.validation.ValidationMessages;
import org.hibernate.exception.ConstraintViolationException;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.Collections;
import java.util.List;

@Provider
public class HibernateConstraintViolationExceptionMapper
        extends AbstractConstraintViolationExceptionMapper<ConstraintViolationException>
        implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        return buildResponse(exception);
    }

    @Override
    protected List<ConstraintViolationBean> exception2json(ConstraintViolationException e) {
        final String messageTemplate = "db.constraint." + e.getConstraintName();
        final ConstraintViolationBean bean = new ConstraintViolationBean(messageTemplate, ValidationMessages.translateMessage(messageTemplate), "");
        return Collections.singletonList(bean);
    }
}
