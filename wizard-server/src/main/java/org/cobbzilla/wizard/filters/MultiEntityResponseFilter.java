package org.cobbzilla.wizard.filters;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import java.util.Collection;
import java.util.Iterator;

import static org.cobbzilla.util.reflect.ReflectionUtil.arrayClass;

public abstract class MultiEntityResponseFilter<T, A extends ApiAccount, P extends ApiProfile> extends EntityResponseFilter<T> {

    protected abstract A getAccount(ContainerRequestContext request);
    protected abstract P getActiveProfile(ContainerRequestContext request, A account);
    protected abstract T filterEntity(T thing, A account, P profile);

    @Override protected boolean shouldFilter(ContainerRequestContext request, ContainerResponseContext response,
                                             String responseClassName, Class<?> responseClass) {
        return super.shouldFilter(request, response, responseClassName, responseClass)
                || (responseClass.isArray() && arrayClass(getMatchEntityClass()).isAssignableFrom(responseClass) && ((Object[]) response.getEntity()).length > 0)
                || (Collection.class.isAssignableFrom(responseClass) && firstElementIsAssignableFrom(response));
    }

    private boolean firstElementIsAssignableFrom(ContainerResponseContext response) {
        final Iterator iter = ((Collection) response.getEntity()).iterator();
        return iter.hasNext() && getMatchEntityClass().isAssignableFrom(iter.next().getClass());
    }

    @Override protected void filter(ContainerRequestContext request, ContainerResponseContext response, String responseClassName, Class<?> responseClass) {
        final A account = getAccount(request);
        final P profile = getActiveProfile(request, account);
        if (disableFilteringFor(account, profile)) return;
        filterByProfile(responseClass, response, account, profile);
    }

    protected boolean disableFilteringFor(A account, P profile) { return false; }

    protected ContainerResponseContext filterByProfile(Class<?> responseClass, ContainerResponseContext response, A account, P profile) {
        final Object entity = response.getEntity();
        if (responseClass.isArray()) {
            for (T thing : (T[]) entity) filterEntity(thing, account, profile);

        } else if (Collection.class.isAssignableFrom(responseClass)) {
            for (T thing : ((Collection<T>) entity)) filterEntity(thing, account, profile);

        } else {
            filterEntity(((T) entity), account, profile);
        }
        return response;
    }

}
