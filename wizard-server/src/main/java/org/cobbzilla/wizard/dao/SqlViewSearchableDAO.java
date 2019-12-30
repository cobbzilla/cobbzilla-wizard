package org.cobbzilla.wizard.dao;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.InvocationHandler;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.cobbzilla.util.collection.ExpirationMap;
import org.cobbzilla.util.reflect.ReflectionUtil;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.RelatedEntities;
import org.cobbzilla.wizard.model.entityconfig.EntityFieldType;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECField;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECSearchable;
import org.cobbzilla.wizard.model.search.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Arrays.asList;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.reflect.ReflectionUtil.instantiate;
import static org.cobbzilla.util.string.StringUtil.camelCaseToSnakeCase;
import static org.cobbzilla.util.string.StringUtil.sqlFilter;
import static org.cobbzilla.wizard.model.entityconfig.EntityFieldType.*;
import static org.cobbzilla.wizard.model.search.SearchBoundComparison.*;

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
        for (Field f : FieldUtils.getAllFields(getEntityClass())) {
            final ECSearchable search = f.getAnnotation(ECSearchable.class);
            if (!f.getName().equalsIgnoreCase(bound) || search == null) continue;

            final SearchField field = new SearchField() {
                @Override public String name() { return camelCaseToSnakeCase(f.getName()); }
                @Override public SearchBound[] getBounds() {
                    List<SearchBound> bounds = new ArrayList<>();
                    final EntityFieldType fieldType;
                    if (!empty(search.bounds())) {
                        fieldType = safeFromString(search.bounds());
                        if (fieldType == null) {
                            try {
                                final SearchBoundBuilder builder = instantiate(search.bounds());
                                return builder.build(bound, value, params, locale);
                            } catch (Exception e) {
                                return die("getBounds(" + bound + "): error invoking  custom SearchBoundBuilder: " + search.bounds() + ": " + e);
                            }
                        }
                    } else {
                        final ECField ecField = f.getAnnotation(ECField.class);
                        fieldType = ecField != null ? ecField.type() : null;
                    }
                    if (fieldType != null) {
                        switch (fieldType) {
                            case epoch_time: case date: case date_past: case date_future:
                            case year: case year_and_month: case year_and_month_past:
                            case year_future: case year_past: case year_and_month_future:
                                bounds.addAll(asList(SearchField.bindTime(name())));
                                break;
                            case flag:
                                bounds.add(eq.bind(name(), SearchFieldType.integer));
                                break;
                            case string:
                                bounds.add(eq.bind(name(), SearchFieldType.string));
                                break;
                        }
                        if (isNullable(f)) {
                            bounds.add(is_null.bind(name()));
                            bounds.add(not_null.bind(name()));
                        }
                        if (safeColumnLength(f) > 500) {
                            bounds.add(like.bind(name()));
                        }
                    }
                    if (empty(bounds)) return die("getBounds: no bounds defined for: "+bound);
                    return bounds.toArray(SearchBound[]::new);
                }
                @Override public String getSort() { return search.sortField(); }
            };
            return SearchField.buildBound(field, value, params, locale);
        }
        return die("buildBound: no bound defined for: "+bound);
    }

    default String getDefaultSort() { return "ctime"; }

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

    @AllArgsConstructor
    class SimpleSearchResultHandler implements InvocationHandler  {

        @Getter private ThreadLocal<Map<String, RelatedEntities>> relatedByUuid = new ThreadLocal<>();

        public SimpleSearchResultHandler () { relatedByUuid.set(new ExpirationMap<>()); }

        @Override public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                case "getRelated":
                    final Object uuid = ReflectionUtil.get(proxy, "uuid");
                    if (uuid == null) return die("getRelated: no uuid found: "+proxy);
                    return this.getRelatedByUuid().get().computeIfAbsent(uuid.toString(), k -> new RelatedEntities());
            }
            return method.invoke(proxy, args);
        }
    }
}
