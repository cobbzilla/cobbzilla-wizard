package org.cobbzilla.wizard.model.entityconfig;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.util.RestResponse;

public interface ModelSetupListener {

    void preCreate (EntityConfig entityConfig, Identifiable entity);
    void postCreate(EntityConfig entityConfig, Identifiable entity, Identifiable created);

    void preUpdate (EntityConfig entityConfig, Identifiable entity);
    void postUpdate(EntityConfig entityConfig, Identifiable entity, Identifiable updated);

    void preEntityConfig (String entityType);
    void postEntityConfig(String entityType, EntityConfig entityConfig);

    void preLookup(Identifiable entity);
    void postLookup(Identifiable entity, Identifiable request, RestResponse response);

    <T extends Identifiable> T subst(T entity);
    ObjectNode jsonSubst(ObjectNode node);
}
