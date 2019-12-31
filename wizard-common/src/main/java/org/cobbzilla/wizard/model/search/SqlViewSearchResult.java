package org.cobbzilla.wizard.model.search;

import org.cobbzilla.util.collection.ExpirationMap;
import org.cobbzilla.util.reflect.ReflectionUtil;
import org.cobbzilla.wizard.model.HasRelatedEntities;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.RelatedEntities;
import org.cobbzilla.wizard.model.SqlDefaultSearchField;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECSearchable;

import java.lang.reflect.Field;
import java.util.Map;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.reflect.ReflectionUtil.fieldsWithAnnotation;

public interface SqlViewSearchResult extends HasRelatedEntities {

    RelatedEntities getRelated();

    Map<String, SearchField> boundCache = new ExpirationMap<>();

    default SearchField searchField(String bound) {
        for (Field f : fieldsWithAnnotation(getClass(), ECSearchable.class)) {
            if (!f.getName().equalsIgnoreCase(bound)) continue;
            final ECSearchable search = f.getAnnotation(ECSearchable.class);
            return boundCache.computeIfAbsent(bound, k -> new SqlDefaultSearchField(f, search, bound));
        }
        return die("genericBound: no bound defined for: "+bound);
    }

    default boolean matches(SqlViewField[] fields, String filter, boolean caseSensitive) {
        final String trueFilter = caseSensitive ? filter : filter.toLowerCase();
        for (SqlViewField field : fields) {
            if (!field.isFilter()) continue;
            final Class<? extends Identifiable> type = field.getType();
            final Object target;
            if (type == null || type.isAssignableFrom(getClass())) {
                target = this;
            } else {
                target = field.hasEntity() ? related().entity(type, field.getEntity()) : related().entity(type);
            }
            final Object value = ReflectionUtil.get(target, field.getEntityProperty(), null);
            if (value != null && (caseSensitive ? value.toString() : value.toString().toLowerCase()).contains(trueFilter)) {
                return true;
            }
        }
        return false;
    }

    default boolean matches(SqlViewField[] fields, String filter) {
        return matches(fields, filter, false);
    }

}
