package org.cobbzilla.wizard.exceptionmappers;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.http.HttpStatusCodes;

@NoArgsConstructor @AllArgsConstructor @Accessors(chain=true)
public class HttpRedirectException extends RuntimeException {

    @Getter @Setter private int status = HttpStatusCodes.FOUND;
    @Getter @Setter private String location = "/";

    public HttpRedirectException (String location) { this.location = location; }

}
