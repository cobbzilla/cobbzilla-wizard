package org.cobbzilla.wizard.model;

import java.util.concurrent.ConcurrentHashMap;

import static org.cobbzilla.util.reflect.ReflectionUtil.instantiate;
import static org.cobbzilla.util.string.StringUtil.uncapitalize;

public class RelatedEntities extends ConcurrentHashMap<String, Identifiable> {

    public <T extends Identifiable> T entity(Class<T> clazz) {
        return entity(clazz, uncapitalize(clazz.getSimpleName()));
    }

    public <T extends Identifiable> T entity(final Class<T> clazz, String name) {
        return (T) computeIfAbsent(name, k -> instantiate(clazz));
    }

}
