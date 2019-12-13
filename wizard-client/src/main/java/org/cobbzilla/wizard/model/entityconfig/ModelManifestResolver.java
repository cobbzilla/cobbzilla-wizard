package org.cobbzilla.wizard.model.entityconfig;

import org.cobbzilla.util.json.JsonUtil;

import java.util.LinkedHashMap;

import static org.cobbzilla.util.io.FileUtil.dirname;
import static org.cobbzilla.util.json.JsonUtil.json;

public interface ModelManifestResolver {

    ModelManifestResolver subResolver(String dirname);

    String jsonAtPath(String path);

    default LinkedHashMap<String, String> buildModel(String path) {
        final String[] models = json(jsonAtPath(path), String[].class, JsonUtil.FULL_MAPPER_ALLOW_COMMENTS);
        final LinkedHashMap<String, String> modelJson = new LinkedHashMap<>(models.length);
        final String parent = dirname(path);
        for (String model : models) {
            modelJson.put(model, jsonAtPath(parent + "/" + model + ".json"));
        }
        return modelJson;
    }

}
