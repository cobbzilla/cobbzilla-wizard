package org.cobbzilla.wizard.model.entityconfig;

public interface ModelMigrationListener extends ModelSetupListener {

    void beforeApplyMigration (ModelVersion version);
    void successfulMigration (ModelVersion version);
    void alreadyAppliedMigration(ModelVersion version);
}
