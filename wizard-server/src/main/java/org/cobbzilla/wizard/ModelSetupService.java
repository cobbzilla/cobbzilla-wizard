package org.cobbzilla.wizard;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.cobbzilla.util.io.FileUtil;
import org.cobbzilla.wizard.api.CrudOperation;
import org.cobbzilla.wizard.client.ApiClientBase;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.entityconfig.EntityConfig;
import org.cobbzilla.wizard.model.entityconfig.ManifestFileResolver;
import org.cobbzilla.wizard.model.entityconfig.ModelSetup;
import org.cobbzilla.wizard.model.entityconfig.ModelSetupListenerBase;
import org.cobbzilla.wizard.server.config.PgRestServerConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;
import static org.cobbzilla.util.io.FileUtil.extension;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.json.JsonUtil.newArrayNode;
import static org.cobbzilla.wizard.Unroll.unrollOrInvalid;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

public abstract class ModelSetupService {

    protected abstract PgRestServerConfiguration getConfiguration();
    protected abstract String getEntityConfigsEndpoint();
    protected abstract void setOwner(Identifiable owner, Identifiable entity);

    public Map<CrudOperation, Collection<Identifiable>> setupModel(Identifiable owner, File modelFile) {
        File modelDir;
        final String ext = extension(modelFile);
        switch (ext.toLowerCase()) {
            case ".zip": case ".gz": case ".tgz":
                modelDir = unrollOrInvalid(modelFile, "err.entity.fileZipFormat.invalid");
                break;
            case ".json":
                modelDir = null;
                break;
            default: throw invalidEx("err.entity.filenameExtension.invalid");
        }

        final LinkedHashMap<String, String> models = new LinkedHashMap<>();

        final ManifestFileResolver resolver;
        if (modelDir == null) {
            resolver = null;

            // It's a JSON file, determine type
            final String filename = modelFile.getName();
            final int dotPos = filename.indexOf('.');
            final int uPos = filename.indexOf('_');
            final String entityClassInFilename = filename.substring(0, Math.min(dotPos == -1 ? filename.length() : dotPos, uPos == -1 ? filename.length() : uPos));
            final Class<? extends Identifiable> entityClass = getConfiguration().getEntityClasses().stream()
                    .filter(c -> c.getSimpleName().equalsIgnoreCase(entityClassInFilename))
                    .findFirst().orElse(null);
            if (entityClass == null) throw invalidEx("err.entity.classInFilename.invalid");

            // Does the file contain a single object? if so, wrap in array
            final JsonNode node = json(FileUtil.toStringOrDie(modelFile), JsonNode.class);
            if (!node.isArray()) {
                final JsonNode arrayNode = newArrayNode().add(node);
                models.put(entityClass.getName(), json(arrayNode));
            } else {
                models.put(entityClass.getName(), json(node));
            }
        } else {
            resolver = new ManifestFileResolver(modelDir);
            final File manifest = new File(modelDir, "manifest.json");
            if (!manifest.exists()) {
                throw invalidEx("err.entity.manifest.required");
            }
            models.put("manifest", manifest.getName());
        }

        try {
            final ApiClientBase api = getConfiguration().newApiClient();
            final ModelSetupForOwner setup = new ModelSetupForOwner(owner);
            return setup.setup(api, models, resolver);

        } catch (Exception e) {
            throw invalidEx("err.entity.setupError", shortError(e));
        }
    }

    @AllArgsConstructor
    private class ModelSetupForOwner extends ModelSetupListenerBase {

        private final Identifiable owner;
        @Getter private final Map<CrudOperation, Collection<Identifiable>> status = new ConcurrentHashMap<>();

        @Override public void preCreate(EntityConfig entityConfig, Identifiable entity) {
            setOwner(owner, entity);
            super.preCreate(entityConfig, entity);
        }

        @Override public void postCreate(EntityConfig entityConfig, Identifiable entity, Identifiable created) {
            status.computeIfAbsent(CrudOperation.create, k -> new ArrayList<>()).add(created);
        }

        @Override public void preUpdate(EntityConfig entityConfig, Identifiable entity) {
            setOwner(owner, entity);
            super.preUpdate(entityConfig, entity);
        }

        @Override public void postUpdate(EntityConfig entityConfig, Identifiable entity, Identifiable updated) {
            status.computeIfAbsent(CrudOperation.update, k -> new ArrayList<>()).add(updated);
        }

        public Map<CrudOperation, Collection<Identifiable>> setup(ApiClientBase api,
                                                                  LinkedHashMap<String, String> models,
                                                                  ManifestFileResolver resolver) throws Exception {
            ModelSetup.setupModel(api, getEntityConfigsEndpoint(), models,
                    resolver, this, true, getClass().getName()+"_"+ owner.getUuid()+"_"+now());
            return status;
        }
    }
}
