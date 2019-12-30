package org.cobbzilla.wizard.dao;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.entityconfig.EntityFieldType;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECSearchable;
import org.cobbzilla.wizard.model.search.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

    default String getSelectClause(SearchQuery searchQuery) { return selectClause(searchQuery); }

    default String selectClause(SearchQuery searchQuery) {
        final SqlViewField[] searchFields = getSearchFields();

        final StringBuilder selectFields = new StringBuilder();
        if (!searchQuery.getHasFields()) {
            if (empty(searchFields)) return "*";
            for (SqlViewField field : searchFields) {
                if (selectFields.length() > 0) selectFields.append(", ");
                selectFields.append(field);
            }
        }

        if (empty(searchFields)) die("getSelectClause: requested specific fields but "+getClass().getSimpleName()+" returned null/empty from getSearchFields()");

        for (String field : searchQuery.getFields()) {
            if (Arrays.stream(searchFields).noneMatch(f -> f.getName().equalsIgnoreCase(field))) {
                return die("getSelectClause: cannot search for field "+field+", add @ECSearchable annotation to enable searching");
            }
            if (selectFields.length() > 0) selectFields.append(", ");
            selectFields.append(field);
        }
        return selectFields.toString();
    }

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
                    final EntityFieldType fieldType = safeFromString(search.bounds());
                    if (fieldType == null) {
                        try {
                            final SearchBoundBuilder builder = instantiate(search.bounds());
                            return builder.build(bound, value, params, locale);
                        } catch (Exception e) {
                            return die("getBounds("+bound+"): error invoking  custom SearchBoundBuilder: "+search.bounds()+": "+e);
                        }
                    }
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

}
