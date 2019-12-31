package org.cobbzilla.wizard.dao;

import lombok.EqualsAndHashCode;
import org.cobbzilla.wizard.model.entityconfig.EntityFieldType;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECField;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECSearchable;
import org.cobbzilla.wizard.model.search.SearchBound;
import org.cobbzilla.wizard.model.search.SearchField;
import org.cobbzilla.wizard.model.search.SearchFieldType;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.reflect.ReflectionUtil.instantiate;
import static org.cobbzilla.util.security.ShaUtil.sha256_hex;
import static org.cobbzilla.util.string.StringUtil.camelCaseToSnakeCase;
import static org.cobbzilla.wizard.model.entityconfig.EntityFieldType.*;
import static org.cobbzilla.wizard.model.search.SearchBoundComparison.*;

@EqualsAndHashCode
public class SqlDefaultSearchField implements SearchField {

    private final Field f;
    private final ECSearchable search;
    private final String bound;
    private final String value;
    private final List<Object> params;
    private final String locale;

    public static String hash (Field f, ECSearchable search, String bound, String value, List<Object> params, String locale) {
        return sha256_hex(hashOf(f, search, bound, value, params, locale));
    }

    public SqlDefaultSearchField(Field f, ECSearchable search, String bound, String value, List<Object> params, String locale) {
        this.f = f;
        this.search = search;
        this.bound = bound;
        this.value = value;
        this.params = params;
        this.locale = locale;
    }

    @Override public String name() { return camelCaseToSnakeCase(f.getName()); }

    @Override public SearchBound[] getBounds() {
        List<SearchBound> bounds = new ArrayList<>();
        EntityFieldType fieldType = search.type();
        if (fieldType == none_set) {
            if (!empty(search.bounds())) {
                try {
                    final SearchBoundBuilder builder = instantiate(search.bounds());
                    return builder.build(bound, value, params, locale);
                } catch (Exception e) {
                    return die("getBounds(" + bound + "): error invoking  custom SearchBoundBuilder: " + search.bounds() + ": " + e);
                }
            } else {
                final ECField ecField = f.getAnnotation(ECField.class);
                fieldType = ecField != null ? ecField.type() : null;
            }
        }
        if (fieldType != null) {
            switch (fieldType) {
                case epoch_time: case date: case date_past: case date_future:
                case year: case year_and_month: case year_and_month_past:
                case year_future: case year_past: case year_and_month_future:
                case expiration_time:
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
        if (empty(bounds)) return die("getBounds: no bounds defined for: "+ bound);
        return bounds.toArray(SearchBound[]::new);
    }

    @Override public String getSort() { return search.sortField(); }
}
