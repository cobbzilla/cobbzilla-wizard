package org.cobbzilla.wizard.model.entityconfig;

import org.cobbzilla.wizard.model.OpenApiSchema;

public interface EntityConfigSource {

    EntityConfig getEntityConfig(Object thing);
    EntityConfig getOrCreateEntityConfig(Object thing, OpenApiSchema schema) throws Exception;
}
