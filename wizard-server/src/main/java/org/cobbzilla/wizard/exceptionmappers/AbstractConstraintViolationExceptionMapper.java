package org.cobbzilla.wizard.exceptionmappers;

import org.cobbzilla.util.http.HttpStatusCodes;
import org.cobbzilla.wizard.validation.ConstraintViolationBean;

import javax.validation.ConstraintViolation;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.cobbzilla.wizard.resources.ResourceUtil.status;

public abstract class AbstractConstraintViolationExceptionMapper<E extends Exception> {

    protected Response buildResponse(E e) { return status(HttpStatusCodes.INVALID, exception2json(e)); }

    protected List<ConstraintViolationBean> exception2json(E e) {
        return Collections.singletonList(mapGenericExceptionToConstraintViolationBean(e));
    }

    protected ConstraintViolationBean mapGenericExceptionToConstraintViolationBean(E e) {
        return new ConstraintViolationBean(scrubMessage(e.getMessage()), e.getMessage(), getInvalidValue(e));
    }

    protected List<ConstraintViolationBean> getConstraintViolationBeans(List<ConstraintViolation> violations) {
        final List<ConstraintViolationBean> jsonList = new ArrayList<>(violations.size());
        for (ConstraintViolation violation : violations) {
            jsonList.add(new ConstraintViolationBean(violation));
        }
        return jsonList;
    }

    protected String scrubMessage(String messageTemplate) {
        return messageTemplate.replace("'", "").replace("{", "").replace("}", "");
    }

    protected String getInvalidValue(Exception e) { return null; }

}
