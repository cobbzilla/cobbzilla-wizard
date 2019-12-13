package org.cobbzilla.wizard.validation;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.cobbzilla.util.reflect.ReflectionUtil;

import java.util.HashMap;
import java.util.Map;

@AllArgsConstructor
public class UniqueValidatorDaoHelper<T> {

    @Getter @Setter private Map<String, Finder<T>> finders = new HashMap<>();

    public boolean isUnique(String uniqueFieldName, Object uniqueValue) {
        if (uniqueValue == null) return true;

        for (String field : finders.keySet()) {
            if (uniqueFieldName.endsWith(field)) {
                final T found = finders.get(field).find(uniqueValue.toString());
                return found == null;
            }
        }

        throw new IllegalArgumentException("entityExists: unsupported uniqueFieldName: "+uniqueFieldName);
    }

    public boolean isUnique(String uniqueFieldName, Object uniqueValue, String idFieldName, Object idValue) {

        if (uniqueValue == null) return true;
        for (String field : finders.keySet()) {

            if (!uniqueFieldName.endsWith(field)) continue;

            final T found = finders.get(field).find(uniqueValue.toString());
            if (found == null) return true;

            return ReflectionUtil.get(found, idFieldName).equals(idValue);
        }
        throw new IllegalArgumentException("isUnique: unsupported uniqueFieldName: "+uniqueFieldName);
    }

    public interface Finder<T> {
        public T find (Object query);
    }

}
