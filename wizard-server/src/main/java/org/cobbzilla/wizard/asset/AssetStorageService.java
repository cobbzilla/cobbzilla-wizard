package org.cobbzilla.wizard.asset;

import org.cobbzilla.util.http.HttpContentTypes;
import org.cobbzilla.util.io.FileUtil;

import java.io.File;
import java.io.InputStream;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_JSON;
import static org.cobbzilla.util.security.ShaUtil.sha256_file;

public abstract class AssetStorageService {

    public static AssetStorageService build(AssetStorageConfiguration configuration) {
        AssetStorageType type = configuration.getType();
        if (type == null) type = AssetStorageType.local;
        switch (type) {
            case local: return new LocalAssetStorageService(configuration.getConfig());
            case s3: return new S3AssetStorageService(configuration.getConfig());
            case resource: return new ResourceStorageService(configuration.getConfig());
            default: return die("AssetStorageService.build: invalid type: "+ type);
        }
    }

    public abstract AssetStream load(String uri);

    public abstract boolean exists(String uri);

    public String store(InputStream fileStream, String filename) { return store(fileStream, filename, null); }

    public abstract String store(InputStream fileStream, String fileName, String uri);

    public abstract void copy(String from, String to);

    public abstract boolean delete(String uri);

    public String getUri(File file, String filename) { return getUri(sha256_file(file), filename); }

    public String getUri(String sha256, String filename) {
        return sha256.substring(0, 2) + "/" + sha256.substring(2, 4) + "/" + sha256.substring(4, 6) + "/" + sha256.substring(6, 8) + "/" + sha256 + FileUtil.extension(filename);
    }

    public String getMimeType(String filename) {
        return filename.endsWith(".json") ? APPLICATION_JSON : HttpContentTypes.contentType(filename);
    }

}
