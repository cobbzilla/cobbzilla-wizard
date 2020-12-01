package org.cobbzilla.wizard.resources;

import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.NameAndValue;
import org.cobbzilla.util.http.HttpContentTypes;
import org.cobbzilla.util.http.HttpResponseBean;
import org.cobbzilla.wizard.api.ApiException;
import org.cobbzilla.wizard.api.ForbiddenException;
import org.cobbzilla.wizard.api.NotFoundException;
import org.cobbzilla.wizard.api.ValidationException;
import org.cobbzilla.wizard.exceptionmappers.HttpRedirectException;
import org.cobbzilla.wizard.stream.ByteStreamingOutput;
import org.cobbzilla.wizard.stream.FileStreamingOutput;
import org.cobbzilla.wizard.stream.SendableResource;
import org.cobbzilla.wizard.stream.StreamStreamingOutput;
import org.cobbzilla.wizard.util.RestResponse;
import org.cobbzilla.wizard.validation.*;
import org.glassfish.jersey.server.ContainerRequest;

import javax.persistence.EntityNotFoundException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.File;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static javax.ws.rs.core.HttpHeaders.*;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.http.HttpContentTypes.*;
import static org.cobbzilla.util.http.HttpStatusCodes.*;
import static org.cobbzilla.util.string.StringUtil.UTF8;

@Slf4j
public class ResourceUtil {

    public static Response ok() { return Response.ok().build(); }

    public static Response ok(Object o) { return Response.ok(o).build(); }

    public static Response ok_utf8(Object o) { return Response.ok(o).header(CONTENT_ENCODING, UTF8).build(); }

    public static Response ok_empty() { return Response.ok(Collections.emptyMap()).build(); }

    public static Response ok_empty_list() { return Response.ok(Collections.emptyList()).build(); }

    public static Response send(SendableResource resource) {
        return send(resource.getOut(), resource.getStatus(), resource.getHeaders(), resource.getName(), resource.getContentType(), resource.getContentLength(), resource.getForceDownload());
    }

    public static Response send(StreamingOutput out, int status, NameAndValue[] headers, String name, String contentType, Long contentLength, Boolean forceDownload) {
        Response.ResponseBuilder builder = Response.status(status).entity(out);
        if (contentType != null) builder = builder.header(CONTENT_TYPE, contentType);
        if (name != null) {
            if (contentType != null && !contentType.equals(APPLICATION_OCTET_STREAM)) {
                final String ext = fileExt(contentType);
                if (!name.endsWith(ext)) name += ext;
            }
            if (forceDownload == null || !forceDownload) {
                builder = builder.header("Content-Disposition", "inline; filename=\"" + name + "\"");
            } else {
                builder = builder.header("Content-Disposition", "attachment; filename=\"" + name + "\"");
            }
        } else if (forceDownload != null && forceDownload) {
            return die("send: forceDownload was true but no filename was set");
        }
        if (contentLength != null) builder = builder.header(CONTENT_LENGTH, contentLength);
        if (headers != null) {
            for (NameAndValue h : headers) {
                if (h.getName().equalsIgnoreCase(CONTENT_TYPE) && contentType != null) {
                    // we already added a Content-Type header, don't add a second one
                    if (log.isDebugEnabled()) log.debug("send: already added "+CONTENT_TYPE+" ("+contentType+"), not adding "+CONTENT_TYPE+" from headers: "+h.getValue());

                } else if (h.getName().equalsIgnoreCase(CONTENT_LENGTH) && contentLength != null) {
                    // we already added a Content-Length header, don't add a second one
                    if (log.isDebugEnabled()) log.debug("send: already added "+CONTENT_LENGTH+" header ("+contentLength+"), not adding "+CONTENT_LENGTH+" from headers: "+h.getValue());

                } else {
                    builder = builder.header(h.getName(), h.getValue());
                }
            }
        }
        return builder.build();
    }

    public static Response accepted() { return Response.status(ACCEPTED).build(); }

    public static Response nonAuthoritative(Object o) {
        return Response.status(NON_AUTHORITATIVE_INFO).entity(o).build();
    }

    public static ResourceHttpException nonAuthoritativeEx(Object o) {
        return new ResourceHttpException(NON_AUTHORITATIVE_INFO).setEntity(o);
    }

    public static Response serverError() { return Response.serverError().build(); }

    public static Response notFound() { return notFound(null); }

    public static Response notFound(String id) {
        if (id == null) id = "-unknown-";
        return status(Response.Status.NOT_FOUND, Collections.singletonMap("resource", id));
    }

    public static Response notFound_blank() { return status(NOT_FOUND); }

    public static EntityNotFoundException notFoundEx() { return notFoundEx(null); }

    public static EntityNotFoundException notFoundEx(String id) {
        if (id == null) id = "-unknown-";
        final EntityNotFoundException e = new EntityNotFoundException(id);
        log.info("Object not found", e);
        return e;
    }

    public static Response status (Response.Status status) { return status(status.getStatusCode()); }
    public static Response status (int status) { return Response.status(status).build(); }
    public static Response status (Response.Status status, Object entity) {
        return entity != null
                ? status(status.getStatusCode(), entity)
                : status(status.getStatusCode());
    }
    public static Response status (int status, Object entity) {
        return entity != null
                ? Response.status(status).type(APPLICATION_JSON).entity(entity).build()
                : status(status);
    }

    public static Response redirect (String location) { return redirect(FOUND, location); }
    public static Response redirect (int status, String location) {
        return Response.status(status).header(LOCATION, location).build();
    }

    public static Response forceRedirect (String location) { return forceRedirect(FOUND, location); }
    public static Response forceRedirect (int status, String location) {
        throw new HttpRedirectException(status, location);
    }

    public static Response forbidden() { return status(FORBIDDEN); }
    public static ResourceHttpException forbiddenEx() { return new ResourceHttpException(FORBIDDEN); }

    public static Response unauthorized() { return status(UNAUTHORIZED); }
    public static ResourceHttpException unauthorizedEx() { return new ResourceHttpException(UNAUTHORIZED); }

    public static Response invalid() { return status(INVALID); }
    public static Response invalid(List<ConstraintViolationBean> violations) { return status(INVALID, violations); }

    public static Response invalid(ConstraintViolationBean violation) {
        final List<ConstraintViolationBean> violations = new ArrayList<>();
        violations.add(violation);
        return invalid(violations);
    }

    public static Response invalid(String messageTemplate) { return invalid(messageTemplate, null); }

    public static Response invalid(String messageTemplate, String invalidValue) {
        return invalid(messageTemplate, messageTemplate, invalidValue);
    }

    public static Response invalid(String messageTemplate, String message, String invalidValue) {
        List<ConstraintViolationBean> violations = new ArrayList<>();
        violations.add(new ConstraintViolationBean(messageTemplate, message, invalidValue));
        return invalid(violations);
    }

    public static Response invalid(SimpleViolationException e) { return invalid(e.getBean()); }

    public static Response invalid(ValidationResult result) { return invalid(result.getViolationBeans()); }

    public static SimpleViolationException invalidEx(String messageTemplate) { return invalidEx(messageTemplate, null, null); }
    public static SimpleViolationException invalidEx(String messageTemplate, String message) { return invalidEx(messageTemplate, message, null); }
    public static SimpleViolationException invalidEx(String messageTemplate, String message, String invalidValue) {
        return invalidEx(messageTemplate, message, invalidValue, true);
    }
    public static SimpleViolationException invalidEx(String messageTemplate, String message, String invalidValue,
                                                     boolean logException) {
        final SimpleViolationException ex = new SimpleViolationException(messageTemplate, message, invalidValue);
        if (logException) log.warn("invalidEx: "+ex, ex);
        return ex;
    }

    public static MultiViolationException invalidEx(ValidationResult result) {
        return invalidEx(result.getViolationBeans());
    }

    public static MultiViolationException invalidEx(List<ConstraintViolationBean> violationBeans) {
        return new MultiViolationException(violationBeans);
    }

    public static Response timeout () { return status(GATEWAY_TIMEOUT); }
    public static ResourceHttpException timeoutEx () { return new ResourceHttpException(GATEWAY_TIMEOUT); }

    public static Response unavailable() { return status(SERVER_UNAVAILABLE); }
    public static ResourceHttpException unavailableEx() { return new ResourceHttpException(SERVER_UNAVAILABLE); }

    public static <T> T userPrincipal(ContainerRequest request) { return userPrincipal(request, true); }

    public static <T> T optionalUserPrincipal(ContainerRequest request) {
        return userPrincipal(request, false);
    }

    public static <T> T userPrincipal(ContainerRequest request, boolean required) {
        T user;
        try {
            user = request == null ? null : (T) request.getSecurityContext().getUserPrincipal();
        } catch (UnsupportedOperationException e) {
            log.debug("userPrincipal: "+e);
            user = null;
        }
        if (required && user == null) throw unauthorizedEx();
        return user;
    }

    public static Response toResponse (ApiException e) {
        if (e instanceof NotFoundException) {
            return notFound(e.getResponse().json);
        } else if (e instanceof ForbiddenException) {
            return forbidden();
        } else if (e instanceof ValidationException) {
            return invalid(new ArrayList<>(((ValidationException) e).getViolations().values()));
        }
        return serverError();
    }

    public static Response toResponse (RestResponse response) {
        Response.ResponseBuilder builder = Response.status(response.status);
        if (response.status/100 == 3) {
            builder = builder.header(LOCATION, response.location);
        }
        if (!empty(response.json)) {
            builder = builder.entity(response.json)
                    .header(CONTENT_TYPE, APPLICATION_JSON)
                    .header(CONTENT_LENGTH, response.json.length());
        }
        return builder.build();
    }

    public static Response toResponse (final HttpResponseBean response) {
        Response.ResponseBuilder builder = Response.status(response.getStatus());

        final List<NameAndValue> headers = response.getHeaders();
        for (NameAndValue header : headers) {
            builder = builder.header(header.getName(), header.getValue());
        }

        if (response.hasEntity()) {
            builder = builder.entity(new ByteStreamingOutput(response.getEntity()));
        }

        return builder.build();
    }

    public static Response streamFile(final File f) {
        if (f == null) return notFound();
        if (!f.exists()) return notFound(f.getName());
        if (!f.canRead()) return forbidden();

        return Response.ok(new FileStreamingOutput(f))
                .header(CONTENT_TYPE, URLConnection.guessContentTypeFromName(f.getName()))
                .header(CONTENT_LENGTH, f.length())
                .build();
    }

    public static Response stream(String contentType, InputStream s) {
        if (contentType == null) contentType = HttpContentTypes.UNKNOWN;
        return Response.ok(new StreamStreamingOutput(s))
                .header(CONTENT_TYPE, contentType)
                .build();
    }

}
