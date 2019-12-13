package org.cobbzilla.wizard.model;

import java.util.concurrent.ConcurrentHashMap;

import static org.cobbzilla.util.reflect.ReflectionUtil.instantiate;
import static org.cobbzilla.util.string.StringUtil.uncapitalize;

public class RelatedEntities extends ConcurrentHashMap<String, Identifiable> {

    public Identifiable entity(Class<? extends Identifiable> clazz) {
        return entity(clazz, uncapitalize(clazz.getSimpleName()));
    }

    public Identifiable entity(final Class<? extends Identifiable> clazz, String name) {
        return computeIfAbsent(name, k -> instantiate(clazz));
    }

}
