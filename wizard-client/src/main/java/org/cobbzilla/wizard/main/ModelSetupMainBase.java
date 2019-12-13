package org.cobbzilla.wizard.main;

import org.cobbzilla.util.io.StreamUtil;
import org.cobbzilla.wizard.auth.LoginRequest;
import org.cobbzilla.wizard.client.ApiClientBase;
import org.cobbzilla.wizard.model.entityconfig.ManifestFileResolver;
import org.cobbzilla.wizard.model.entityconfig.ModelManifestResolver;
import org.cobbzilla.wizard.model.entityconfig.ModelSetup;
import org.cobbzilla.wizard.model.entityconfig.ModelSetupListener;

import java.io.File;
import java.util.LinkedHashMap;

import static org.cobbzilla.util.daemon.ZillaRuntime.notSupported;

public abstract class ModelSetupMainBase<OPT extends ModelSetupOptionsBase> extends MainApiBase<OPT> {

    @Override protected Object buildLoginRequest(OPT options) {
        return new LoginRequest(options.getAccount(), options.getPassword());
    }

    @Override protected void setSecondFactor(Object loginRequest, String token) {
        notSupported("setSecondFactor");
    }

    @Override protected void run() throws Exception {
        final OPT options = getOptions();
        final ApiClientBase api = getApiClient();

        final LinkedHashMap<String, String> models;
        final ModelManifestResolver resolver;
        if (options.hasManifest()) {
            final File manifest = options.getManifest();
            resolver = new ManifestFileResolver(manifest.getParentFile());
            models = resolver.buildModel(manifest.getName());

        } else {
            models = new LinkedHashMap<>();
            models.put(options.getEntityType(), StreamUtil.toString(options.getInStream()));
            resolver = new ManifestFileResolver();
        }

        // todo: perhaps a logging listener?
        final ModelSetupListener listener = getListener();

        try {
            ModelSetup.setupModel(api, options.getEntityConfigUrl(), models, resolver, listener, options.isUpdate(), getClass().getName());
        } catch (Exception e) {
            err("Error setting up model: "+e.getClass().getName()+": "+e.getMessage());
        }
    }

    protected ModelSetupListener getListener() { return null; }

}
