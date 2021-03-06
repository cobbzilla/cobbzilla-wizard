package org.cobbzilla.wizard.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.cobbzilla.util.collection.ExpirationMap;
import org.cobbzilla.wizard.api.CrudOperation;
import org.cobbzilla.wizard.client.ApiClientBase;
import org.cobbzilla.wizard.model.entityconfig.*;
import org.cobbzilla.wizard.server.config.PgRestServerConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.io.FileUtil.*;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.json.JsonUtil.newArrayNode;
import static org.cobbzilla.wizard.util.Unroll.unrollOrInvalid;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

public abstract class ModelSetupService {

    protected abstract PgRestServerConfiguration getConfiguration();
    protected abstract String getEntityConfigsEndpoint();
    protected abstract void setOwner(Identifiable owner, Identifiable entity);

    protected String getClasspathPrefix() { return "models/"; }

    public String getRunName(Identifiable owner) { return getClass().getName() + "_" + owner.getUuid() + "_" + now(); }

    private Map<String, ModelSetupForOwner> setupCache = new ExpirationMap<>();

    public ModelSetupForOwner getSetupObject(Identifiable owner) {
        return setupCache.computeIfAbsent(owner.getUuid(), k -> new ModelSetupForOwner(owner));
    }

    public Map<CrudOperation, Collection<Identifiable>> setupModel(ApiClientBase api, Identifiable owner, String manifest) {
        try {
            final ModelSetupForOwner listener = getSetupObject(owner);
            ModelSetup.setupModel(api, getEntityConfigsEndpoint(), getClasspathPrefix(), manifest, listener, getRunName(owner));
            return listener.getStatus();

        } catch (Exception e) {
            return die("setupModel: "+shortError(e));
        }
    }

    public Map<CrudOperation, Collection<Identifiable>> setupModel(ApiClientBase api, Identifiable owner, File modelFile) {
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
            final JsonNode node = json(toStringOrDie(modelFile), JsonNode.class);
            if (!node.isArray()) {
                final JsonNode arrayNode = newArrayNode().add(node);
                models.put(entityClass.getSimpleName(), json(arrayNode));
            } else {
                models.put(entityClass.getSimpleName(), json(node));
            }
        } else {
            resolver = new ManifestFileResolver(modelDir);
            final File manifest = new File(modelDir, "manifest.json");
            if (!manifest.exists()) {
                throw invalidEx("err.entity.manifest.required");
            }
            models.put("manifest", toStringOrDie(manifest));
        }

        try {
            return getSetupObject(owner).setup(api, models, resolver);

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
                    resolver, this, true, getRunName(owner));
            return status;
        }
    }
}
