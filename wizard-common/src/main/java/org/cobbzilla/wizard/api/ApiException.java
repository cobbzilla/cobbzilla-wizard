package org.cobbzilla.wizard.api;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import org.cobbzilla.util.http.HttpRequestBean;
import org.cobbzilla.wizard.util.RestResponse;

@AllArgsConstructor @ToString
public class ApiException extends RuntimeException {

    @Getter private HttpRequestBean request;
    @Getter private final RestResponse response;

    public ApiException (RestResponse response) {
        super(response.status+": "+response.json);
        this.response = response;
    }

    @Override public String getMessage () { return response.status + ": " + response.json; }

}
