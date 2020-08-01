package org.cobbzilla.wizard.model.entityconfig;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.util.RestResponse;

public class ModelSetupListenerBase implements ModelSetupListener {

    @Override public void preCreate (EntityConfig entityConfig, Identifiable entity, ObjectNode originalJsonRequest) {}
    @Override public void postCreate(EntityConfig entityConfig, Identifiable entity, Identifiable created) {}

    @Override public void preUpdate (EntityConfig entityConfig, Identifiable entity, ObjectNode originalJsonRequest) {}
    @Override public void postUpdate(EntityConfig entityConfig, Identifiable entity, Identifiable updated) {}

    @Override public void preEntityConfig (String entityType) {}
    @Override public void postEntityConfig(String entityType, EntityConfig entityConfig) {}

    @Override public void preLookup(Identifiable entity) {}
    @Override public void postLookup(Identifiable entity, Identifiable request, RestResponse response) {}
    @Override public Identifiable subst (Identifiable entity) { return entity; }
    @Override public ObjectNode jsonSubst (ObjectNode node) { return node; }

}
