package org.cobbzilla.wizard.api;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import org.cobbzilla.util.http.HttpRequestBean;
import org.cobbzilla.wizard.util.RestResponse;

import static org.cobbzilla.util.json.JsonUtil.json;

@AllArgsConstructor @ToString
public class ApiException extends RuntimeException {

    @Getter private HttpRequestBean request;
    @Getter private RestResponse response;

    public ApiException (RestResponse response) {
        super(response.status+": "+response.json);
        this.response = response;
    }

    @Override public String getMessage () { return response.json; }

}
