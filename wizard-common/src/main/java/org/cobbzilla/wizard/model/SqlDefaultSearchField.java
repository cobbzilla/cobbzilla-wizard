package org.cobbzilla.wizard.model;

import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.model.entityconfig.EntityFieldType;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECField;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECForeignKey;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECSearchable;
import org.cobbzilla.wizard.model.search.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.reflect.ReflectionUtil.instantiate;
import static org.cobbzilla.util.string.StringUtil.camelCaseToSnakeCase;
import static org.cobbzilla.wizard.model.Identifiable.UUID;
import static org.cobbzilla.wizard.model.entityconfig.EntityFieldType.*;
import static org.cobbzilla.wizard.model.search.SearchField.*;

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
        if (!empty(search.bounds())) {
            try {
                final SearchBoundBuilder builder = instantiate(search.bounds());
                return builder.build(bound, value, params, locale);
            } catch (Exception e) {
                return die("getBounds(" + bound + "): error invoking  custom SearchBoundBuilder: " + search.bounds() + ": " + e);
            }

        } else if (!empty(search.operators())) {
            for (SearchBoundComparison op : search.operators()) {
                bounds.add(op.bind(name(), SearchFieldType.string));
            }

        } else if (fieldType == none_set) {
            final ECField ecField = f.getAnnotation(ECField.class);
            final ECForeignKey ecForeignKey = f.getAnnotation(ECForeignKey.class);
            if (ecForeignKey != null) {
                fieldType = reference;
            } else {
                fieldType = ecField != null && ecField.type() != none_set ? ecField.type() : guessFieldType(f);
            }
        }
        if (fieldType != null) {
            switch (fieldType) {
                case epoch_time: case date: case date_past: case date_future:
                case year: case year_and_month: case year_and_month_past:
                case year_future: case year_past: case year_and_month_future:
                case expiration_time:
                    bounds.addAll(asList(bindTime(name())));
                    break;
                case integer: case money_integer: case time_duration:
                    bounds.addAll(asList(bindInteger(name())));
                    break;
                case decimal: case money_decimal:
                    bounds.addAll(asList(bindDecimal(name())));
                    break;
                case flag:
                    bounds.addAll(asList(bindBoolean(name())));
                    break;
                case reference:
                    bounds.addAll(asList(bindUuid(name())));
                    break;
                case http_url: case us_phone: case us_state: case us_zip:
                case email: case time_zone: case locale: case currency:
                case ip4: case ip6: case hostname: case fqdn:
                case error: case opaque_string:
                    bounds.addAll(asList(bindNonSortableString(name())));
                    break;
                case string: case json: case json_array:
                    if (f.getName().equals(UUID)) {
                        bounds.addAll(asList(bindUuid(name())));
                    } else {
                        bounds.addAll(asList(bindString(name())));
                    }
                    break;
            }
            if (isNullable(f)) bounds.addAll(asList(bindNullable(name())));
        }
        if (empty(bounds)) return die("getBounds: no bounds defined for: "+ bound);
        return bounds.toArray(SearchBound[]::new);
    }

    @Override public String getSort() { return search.sortField(); }
}
