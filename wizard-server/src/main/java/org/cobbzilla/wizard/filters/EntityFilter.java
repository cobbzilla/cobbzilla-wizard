package org.cobbzilla.wizard.filters;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import java.io.IOException;
import java.lang.reflect.Type;

import static org.cobbzilla.util.reflect.ReflectionUtil.forName;
import static org.cobbzilla.util.reflect.ReflectionUtil.getFirstTypeParam;

@Slf4j
public abstract class EntityFilter<T> implements ContainerResponseFilter {

    @Getter(lazy=true) private final Class<T> matchEntityClass = getFirstTypeParam(getClass());

    @Override public void filter(ContainerRequestContext request, ContainerResponseContext response) throws IOException {

        final Type entityType = response.getEntityType();
        if (entityType == null) return;

        final Class<?> responseClass;
        final String responseClassName;
        try {
            responseClassName = entityType.toString().split(" ")[1];
            responseClass = forName(responseClassName);
        } catch (Exception e) {
            log.warn("filter: error with '" + entityType + "': " + e);
            return;
        }

        if (shouldFilter(request, response, responseClassName, responseClass)) {
            filter(request, response, responseClassName, responseClass);
        }
    }

    protected boolean shouldFilter(ContainerRequestContext request, ContainerResponseContext response, String responseClassName, Class<?> responseClass) {
        return getMatchEntityClass().isAssignableFrom(responseClass);
    }

    protected void filter(ContainerRequestContext request, ContainerResponseContext response, String responseClassName, Class<?> responseClass) {}

}