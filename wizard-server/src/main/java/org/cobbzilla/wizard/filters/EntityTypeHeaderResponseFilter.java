package org.cobbzilla.wizard.filters;

import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.model.search.SearchResults;
import org.cobbzilla.wizard.model.Identifiable;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

@Slf4j
public class EntityTypeHeaderResponseFilter extends EntityResponseFilter<Object> {

    protected String getTypeHeaderName() { return Identifiable.ENTITY_TYPE_HEADER_NAME; }

    @Override public void filter(ContainerRequestContext request,
                                 ContainerResponseContext response,
                                 String responseClassName,
                                 Class<?> responseClass) {

        final boolean isSearchResults = SearchResults.class.isAssignableFrom(responseClass);
        final boolean isCollection = Collection.class.isAssignableFrom(responseClass);
        final boolean isMap = Map.class.isAssignableFrom(responseClass);
        final boolean isArray = responseClass.isArray();
        String elementClassName;
        if (isSearchResults) {
            final SearchResults searchResults = (SearchResults) response.getEntity();
            response.getHeaders().add(getTypeHeaderName(),
                                       SearchResults.class.getName() + (searchResults.hasResults() ? "<" + getCollectionElementClass(searchResults.getResults()) + ">" : ""));

        } else if (isCollection) {
            response.getHeaders().add(getTypeHeaderName(), getCollectionElementClass((Collection) response.getEntity())+"[]");

        } else if (isArray) {
            final Object[] a = (Object[]) response.getEntity();
            try {
                elementClassName = empty(a) ? "" : a[0].getClass().getName();
            } catch (Exception e) {
                elementClassName = "";
            }
            response.getHeaders().add(getTypeHeaderName(), elementClassName + "[]");

        } else if (isMap) {
            response.getHeaders().add(getTypeHeaderName(), LinkedHashMap.class.getName());
        } else {
            response.getHeaders().add(getTypeHeaderName(), responseClassName);
        }
        return;
    }

    protected String getCollectionElementClass(Collection c) {
        try {
            return empty(c) ? "" : c.iterator().next().getClass().getName();
        } catch (Exception ignored) {
            return "";
        }
    }

}