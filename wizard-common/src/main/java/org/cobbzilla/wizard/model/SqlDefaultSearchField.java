package org.cobbzilla.wizard.model;

import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.model.entityconfig.EntityFieldType;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECField;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECSearchable;
import org.cobbzilla.wizard.model.search.SearchBound;
import org.cobbzilla.wizard.model.search.SearchBoundBuilder;
import org.cobbzilla.wizard.model.search.SearchField;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.reflect.ReflectionUtil.instantiate;
import static org.cobbzilla.util.string.StringUtil.camelCaseToSnakeCase;
import static org.cobbzilla.wizard.model.entityconfig.EntityFieldType.*;

@EqualsAndHashCode @Slf4j
public class SqlDefaultSearchField implements SearchField {

    private final Field f;
    private final ECSearchable search;
    private final String bound;
    private final String value;
    private final List<Object> params;
    private final String locale;

    public SqlDefaultSearchField(Field f, ECSearchable search, String bound) {
        this(f, search, bound, null, null, null);
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
        final List<SearchBound> bounds = new ArrayList<>();
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
                fieldType = ecField != null ? ecField.type() : guessFieldType(f);
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
                case integer: case money_integer:
                    bounds.addAll(asList(SearchField.bindInteger(name())));
                    break;
                case decimal: case money_decimal:
                    bounds.addAll(asList(SearchField.bindDecimal(name())));
                    break;
                case flag:
                    bounds.addAll(asList(SearchField.bindBoolean(name())));
                    break;
                case string: case email: case time_zone: case locale:
                case ip4: case ip6: case http_url:
                case us_phone: case us_state: case us_zip:
                    if (f.getName().equals("uuid")) {
                        bounds.addAll(asList(SearchField.bindUuid(name())));
                    } else {
                        bounds.addAll(asList(SearchField.bindString(name())));
                    }
                    break;
            }
            if (isNullable(f)) bounds.addAll(asList(SearchField.bindNullable(name())));
        }
        if (empty(bounds)) return die("getBounds: no bounds defined for: "+ bound);
        return bounds.toArray(SearchBound[]::new);
    }

    @Override public String getSort() { return search.sortField(); }
}