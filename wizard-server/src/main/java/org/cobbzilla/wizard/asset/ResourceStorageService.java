package org.cobbzilla.wizard.asset;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.io.StreamUtil;

import java.io.InputStream;
import java.util.Map;

import static org.cobbzilla.util.daemon.ZillaRuntime.notSupported;

@NoArgsConstructor @Accessors(chain=true) @Slf4j
public class ResourceStorageService extends AssetStorageService {

    public static final String PROP_BASE = "base";

    @Getter @Setter private String base;

    public ResourceStorageService(String base) { this.base = base; }

    public ResourceStorageService(Map<String, String> config) { this(config.get(PROP_BASE)); }

    @Override public AssetStream load(String uri) {
        try {
            return new AssetStream(uri, StreamUtil.loadResourceAsStream(base + "/" + uri), getMimeType(uri));
        } catch (Exception e) {
            log.error("load: "+e);
        }
        return null;
    }

    @Override public boolean exists(String uri) { return load(uri) != null; }

    @Override public String store(InputStream fileStream, String fileName, String uri) { return notSupported(); }

    @Override public void copy(String from, String to) { notSupported(); }

    @Override public boolean delete(String uri) { return notSupported(); }

}
