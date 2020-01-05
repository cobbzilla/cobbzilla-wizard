package org.cobbzilla.wizard.model.entityconfig;


import org.cobbzilla.wizard.client.ApiClientBase;

import java.io.File;
import java.util.Collection;
import java.util.List;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.daemon.ZillaRuntime.realNow;
import static org.cobbzilla.util.reflect.ReflectionUtil.arrayClass;

public class ModelMigration {

    public static <V extends ModelVersion> ModelMigrationResult migrate(ApiClientBase api,
                                                                        String entityConfigUrl,
                                                                        Class<V> modelVersionClass,
                                                                        String modelVersionEndpoint,
                                                                        File localMigrationsDir,
                                                                        List<Integer> migrationsToApply,
                                                                        ModelMigrationListener listener,
                                                                        String callerName) throws Exception {

        // find/sort local migrations
        final Collection<ModelVersion> localMigrations = ModelVersion.fromBaseDir(localMigrationsDir);

        // if we are supposed to apply specific migrations, do that now
        final ModelMigrationResult result = new ModelMigrationResult();
        if (!empty(migrationsToApply)) {
            for (Integer migrationNumber : migrationsToApply) {
                boolean found = false;
                for (ModelVersion migration : localMigrations) {
                    if (migration.getVersion() == migrationNumber) {
                        applyMigration(api, modelVersionEndpoint, entityConfigUrl, listener, migration, callerName);
                        result.incrNumApplied();
                        result.setLatestApplied(migrationNumber);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    die("local migration not found: "+migrationNumber);
                }
            }
            return result;
        }

        // otherwise try to apply all local migrations that have not been applied remotely

        // find remote migrations
        final Class<V[]> modelArrayClass = (Class<V[]>) arrayClass(modelVersionClass);
        final V[] remoteMigrations = api.get(modelVersionEndpoint, modelArrayClass);

        result.setCurrentRemoteVersion(findCurrentRemoteVersion(remoteMigrations));

        for (ModelVersion localMigration : localMigrations) {
            // is there a corresponding remote migration?
            final ModelVersion remoteMigration = findSuccessfulMigration(remoteMigrations, localMigration.getVersion());
            if (remoteMigration == null && localMigration.getVersion() > result.getCurrentRemoteVersion()) {
                if (listener != null) listener.beforeApplyMigration(localMigration);
                applyMigration(api, modelVersionEndpoint, entityConfigUrl, listener, localMigration, callerName);
                if (listener != null) listener.successfulMigration(localMigration);
                result.incrNumApplied();
                result.setLatestApplied(localMigration.getVersion());

            } else if (remoteMigration != null && !remoteMigration.getHash().equals(localMigration.getHash())) {
                die("remote migration ("+remoteMigration+") has different hash than local migration ("+localMigration+")");
            } else {
                if (listener != null) listener.alreadyAppliedMigration(localMigration);
                result.getAlreadyApplied().add(localMigration.getVersion());
            }
        }

        return result;
    }

    private static <V extends ModelVersion> int findCurrentRemoteVersion(V[] migrations) {
        int latestSuccess = -1;
        for (V migration : migrations) if (migration.isSuccess() && migration.getVersion() > latestSuccess) latestSuccess = migration.getVersion();
        return latestSuccess;
    }

    private static ModelVersion findSuccessfulMigration(ModelVersion[] migrations, int version) {
        for (ModelVersion v : migrations) if (v.getVersion() == version && v.isSuccess()) return v;
        return null;
    }

    private static void applyMigration(ApiClientBase api,
                                       String modelVersionEndpoint,
                                       String entityConfigUrl,
                                       ModelSetupListener listener,
                                       ModelVersion migration,
                                       String callerName) throws Exception {
        api.put(modelVersionEndpoint, migration);
        final long start = realNow();
        ModelSetup.setupModel(api, entityConfigUrl, migration.getModels(), new ManifestFileResolver(), listener, callerName);
        migration.setExecutionTime(realNow() - start).setSuccess(true);
        api.put(modelVersionEndpoint, migration);
    }

}
