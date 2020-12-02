package org.cobbzilla.wizard.model.entityconfig;

import org.cobbzilla.util.reflect.OpenApiSchema;

public interface EntityConfigSource {

    EntityConfig getEntityConfig(Object thing);
    EntityConfig getOrCreateEntityConfig(Object thing, OpenApiSchema schema) throws Exception;
}
