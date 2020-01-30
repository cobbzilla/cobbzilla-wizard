package org.cobbzilla.wizard.dao;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.InvocationHandler;
import org.cobbzilla.util.collection.ExpirationMap;
import org.cobbzilla.util.reflect.ReflectionUtil;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.RelatedEntities;
import org.cobbzilla.wizard.model.SqlDefaultSearchField;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECSearchable;
import org.cobbzilla.wizard.model.search.SearchField;
import org.cobbzilla.wizard.model.search.SearchQuery;
import org.cobbzilla.wizard.model.search.SqlViewField;
import org.cobbzilla.wizard.model.search.SqlViewSearchResult;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.reflect.ReflectionUtil.fieldsWithAnnotation;
import static org.cobbzilla.util.string.StringUtil.camelCaseToSnakeCase;
import static org.cobbzilla.util.string.StringUtil.sqlFilter;
import static org.cobbzilla.wizard.model.Identifiable.CTIME;
import static org.cobbzilla.wizard.model.Identifiable.UUID;

public interface SqlViewSearchableDAO<T extends Identifiable> extends DAO<T> {

    String getSearchView();

    default String fixedFilters() { return "1=1"; }

    SqlViewField[] getSearchFields();

    default String getSortField(String sortField) { return camelCaseToSnakeCase(sortField); }

    default String[] getSearchFieldNames() {
        final SqlViewField[] searchFields = getSearchFields();
        final String[] names = new String[searchFields.length];
        for (int i=0; i<searchFields.length; i++) names[i] = searchFields[i].getName();
        return names;
    }

    default String buildFilter(SearchQuery searchQuery, List<Object> params) {
        final String filter = sqlFilter(searchQuery.getFilter());
        final SqlViewField[] fields = getSearchFields();
        int filterCount = 0;
        final StringBuilder b = new StringBuilder();
        for (SqlViewField f : fields) {
            if (f.isFilter()) {
                filterCount++;
                if (b.length() > 0) b.append(" OR ");
                b.append(f.getName()).append(" ilike ?");
            }
        }
        for (int i=0; i<filterCount; i++) params.add(filter);
        return b.toString();
    }

    default String buildBound(String bound, String value, List<Object> params, String locale) {
        for (Field f : fieldsWithAnnotation(getEntityClass(), ECSearchable.class)) {
            if (!f.getName().equalsIgnoreCase(bound)) continue;
            final ECSearchable search = f.getAnnotation(ECSearchable.class);
            final SearchField field = new SqlDefaultSearchField(f, search, bound, value, params, locale);
            return SearchField.buildBound(field, value, params, locale);
        }
        return die("buildBound: no bound defined for: "+bound);
    }

    default String getDefaultSort() { return CTIME; }

    String getSelectClause(SearchQuery searchQuery);

    Map<String, Class<? extends SqlViewSearchResult>> _resultClassCache = new ConcurrentHashMap<>();

    default Class<? extends SqlViewSearchResult> getResultClass() {
        final Class<T> entityClass = getEntityClass();
        if (SqlViewSearchResult.class.isAssignableFrom(entityClass)) return (Class<? extends SqlViewSearchResult>) entityClass;
        return _resultClassCache.computeIfAbsent(entityClass.getName(), k -> {
            final Enhancer enhancer = new Enhancer();
            enhancer.setInterfaces(new Class[]{SqlViewSearchResult.class});
            enhancer.setSuperclass(entityClass);
            enhancer.setCallback(new SimpleSearchResultHandler());
            return (Class<? extends SqlViewSearchResult>) enhancer.create().getClass();
        });
    }

    default boolean encryptedSearchEnabled() { return false; }

    @AllArgsConstructor
    class SimpleSearchResultHandler implements InvocationHandler  {

        @Getter private ThreadLocal<Map<String, RelatedEntities>> relatedByUuid = new ThreadLocal<>();

        public SimpleSearchResultHandler () { relatedByUuid.set(new ExpirationMap<>()); }

        @Override public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                case "getRelated":
                    final Object uuid = ReflectionUtil.get(proxy, UUID);
                    if (uuid == null) return die("getRelated: no uuid found: "+proxy);
                    return this.getRelatedByUuid().get().computeIfAbsent(uuid.toString(), k -> new RelatedEntities());
            }
            return method.invoke(proxy, args);
        }
    }

}
