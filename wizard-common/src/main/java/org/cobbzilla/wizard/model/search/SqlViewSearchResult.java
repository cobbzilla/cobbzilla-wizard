package org.cobbzilla.wizard.model.search;

import org.cobbzilla.util.collection.ExpirationMap;
import org.cobbzilla.wizard.model.HasRelatedEntities;
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

}
