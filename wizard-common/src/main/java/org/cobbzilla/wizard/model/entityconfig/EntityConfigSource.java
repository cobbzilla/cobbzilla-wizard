package org.cobbzilla.wizard.model.entityconfig;

public interface EntityConfigSource {

    EntityConfig getEntityConfig(Object thing);
    EntityConfig getOrCreateEntityConfig(Object thing) throws Exception;
}
