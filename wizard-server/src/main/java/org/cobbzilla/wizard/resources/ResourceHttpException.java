package org.cobbzilla.wizard.resources;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import javax.ws.rs.core.Response;

@NoArgsConstructor @AllArgsConstructor @Accessors(chain=true)
public class ResourceHttpException extends RuntimeException {

    @Getter @Setter private int status;
    @Getter @Setter private Object entity;

    public ResourceHttpException(int status) { this.status = status; }

    public ResourceHttpException(Response response) {
        setStatus(response.getStatus());
        setEntity(response.getEntity());
    }

    public int getStatusClass () { return status / 100; }

}
