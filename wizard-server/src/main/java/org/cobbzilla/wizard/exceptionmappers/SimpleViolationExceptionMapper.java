package org.cobbzilla.wizard.exceptionmappers;

import org.cobbzilla.util.http.HttpStatusCodes;
import org.cobbzilla.wizard.validation.ConstraintViolationBean;
import org.cobbzilla.wizard.validation.SimpleViolationException;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.ArrayList;
import java.util.List;

import static org.cobbzilla.wizard.resources.ResourceUtil.status;

@Provider
public class SimpleViolationExceptionMapper implements ExceptionMapper<SimpleViolationException> {

    @Override
    public Response toResponse(SimpleViolationException e) {
        return status(HttpStatusCodes.INVALID, getEntity(e));
    }

    protected List<ConstraintViolationBean> getEntity(SimpleViolationException e) {
        final List<ConstraintViolationBean> jsonList = new ArrayList<>(1);
        jsonList.add(new ConstraintViolationBean(e.getMessageTemplate(), e.getMessage(), e.getInvalidValue()));
        return jsonList;
    }

}
