package org.cobbzilla.wizard.api;

import org.cobbzilla.util.http.HttpRequestBean;
import org.cobbzilla.wizard.util.RestResponse;

public class NotFoundException extends ApiException {

    public NotFoundException(RestResponse response) { this(null, response); }
    public NotFoundException(HttpRequestBean request, RestResponse response) { super(request, response); }

}
