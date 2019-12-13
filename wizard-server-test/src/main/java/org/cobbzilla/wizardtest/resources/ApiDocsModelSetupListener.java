package org.cobbzilla.wizardtest.resources;

import lombok.AllArgsConstructor;
import org.cobbzilla.restex.targets.TemplateCaptureTarget;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.entityconfig.EntityConfig;
import org.cobbzilla.wizard.model.entityconfig.ModelSetupListenerBase;

@AllArgsConstructor
public class ApiDocsModelSetupListener extends ModelSetupListenerBase {

    private TemplateCaptureTarget apiDocs;

    protected void note(String s) { if (apiDocs != null) apiDocs.addNote(s); }

    @Override public void preCreate (EntityConfig entityConfig, Identifiable entity) { note("Create " + entityConfig.getName()); }
    @Override public void preUpdate (EntityConfig entityConfig, Identifiable entity) { note("Update " + entityConfig.getName()); }

    @Override public void preEntityConfig (String entityType) { note("Lookup EntityConfig for " + entityType); }

    @Override public void preLookup (Identifiable entity)     { note("Checking to see if " + entity.getClass().getSimpleName() + " already exists: " + entity); }

}
