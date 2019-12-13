package org.cobbzilla.wizard.exceptionmappers;

import org.cobbzilla.wizard.validation.ConstraintViolationBean;
import org.springframework.dao.DataIntegrityViolationException;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.Collections;
import java.util.List;

import static org.cobbzilla.wizard.server.RestServerBase.reportError;

@Provider
public class DataIntegrityViolationExceptionMapper
        extends AbstractConstraintViolationExceptionMapper<DataIntegrityViolationException>
        implements ExceptionMapper<DataIntegrityViolationException> {

    @Override public Response toResponse(DataIntegrityViolationException exception) {
        reportError(exception);
        return buildResponse(exception);
    }

    @Override protected List<ConstraintViolationBean> exception2json(DataIntegrityViolationException e) {
        final String messageTemplate = "db.integrity." + e.getMessage().replaceAll("\\W", "_");
        final ConstraintViolationBean bean = new ConstraintViolationBean(messageTemplate, e.getLocalizedMessage(), "");
        return Collections.singletonList(bean);
    }
}
