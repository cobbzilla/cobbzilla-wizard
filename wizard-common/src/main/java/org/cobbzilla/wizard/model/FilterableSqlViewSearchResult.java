package org.cobbzilla.wizard.model;

import org.cobbzilla.util.reflect.ReflectionUtil;
import org.cobbzilla.wizard.model.search.SqlViewField;
import org.cobbzilla.wizard.model.search.SqlViewSearchResult;

public interface FilterableSqlViewSearchResult extends SqlViewSearchResult {

    SqlViewField[] getFilterFields();

    default boolean matches(String filter, boolean caseSensitive) {
        final String trueFilter = caseSensitive ? filter : filter.toLowerCase();
        for (SqlViewField field : getFilterFields()) {
            if (!field.isFilter()) continue;
            final Class<? extends Identifiable> type = field.getType();
            final Object target;
            if (type != null) {
                target = field.hasEntity() ? getRelated().entity(type, field.getEntity()) : getRelated().entity(type);
            } else {
                target = this;
            }
            final Object value = ReflectionUtil.get(target, field.getEntityProperty(), null);
            if (value != null
                    && (caseSensitive ? value.toString() : value.toString().toLowerCase()).contains(trueFilter)) {
                return true;
            }
        }
        return false;
    }

    default boolean matches(String filter) {
        return matches(filter, false);
    }

}
