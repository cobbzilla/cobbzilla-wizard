package org.cobbzilla.wizard.api;

import lombok.Getter;
import org.cobbzilla.util.http.HttpRequestBean;
import org.cobbzilla.util.json.JsonUtil;
import org.cobbzilla.util.string.StringUtil;
import org.cobbzilla.wizard.util.RestResponse;
import org.cobbzilla.wizard.validation.ConstraintViolationBean;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

public class ValidationException extends ApiException {

    @Getter private Map<String, ConstraintViolationBean> violations;
    public boolean hasViolations () { return !empty(violations); }

    public ValidationException (RestResponse response) { this(null, response); }

    public ValidationException (HttpRequestBean request, RestResponse response) {
        super(request, response);
        this.violations = mapViolations(response.json);
    }

    @Override public String getMessage() { return StringUtil.toString(violations.values()); }

    protected Map<String, ConstraintViolationBean> mapViolations(String json) {
        final Map<String, ConstraintViolationBean> map = new HashMap<>();
        final ConstraintViolationBean[] violations;
        try {
            violations = JsonUtil.FULL_MAPPER.readValue(json, ConstraintViolationBean[].class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Error parsing as ConstraintViolationBean[]: "+json+": "+e);
        }
        for (ConstraintViolationBean violation : violations) {
            map.put(violation.getMessageTemplate(), violation);
        }
        return map;
    }
}
