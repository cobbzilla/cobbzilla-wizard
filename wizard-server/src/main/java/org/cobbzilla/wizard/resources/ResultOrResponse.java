package org.cobbzilla.wizard.resources;

import lombok.Getter;

import javax.ws.rs.core.Response;

public class ResultOrResponse<T> {

    @Getter private Response response;
    @Getter private T result;

    public ResultOrResponse (Response response) { this.response = response; }
    public ResultOrResponse (T result) { this.result = result; }

    public boolean hasResponse () { return response != null; }
    public boolean hasResult () { return result != null; }

}
