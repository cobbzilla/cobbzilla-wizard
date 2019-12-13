package org.cobbzilla.wizard.model.entityconfig;

import static org.cobbzilla.util.io.StreamUtil.stream2string;
import static org.cobbzilla.util.string.StringUtil.chop;

public class ManifestClasspathResolver implements ModelManifestResolver {

    private final String prefix;

    public ManifestClasspathResolver(String prefix) {
        this.prefix = chop(prefix, "/");
    }

    @Override public ModelManifestResolver subResolver(String dirname) {
        return new ManifestClasspathResolver(toPath(dirname));
    }

    @Override public String jsonAtPath(String path) { return stream2string(toPath(path)); }

    public String toPath(String dirname) {
        final int prefixIndex = dirname.indexOf(prefix);
        if (prefixIndex != -1) {
            dirname = dirname.substring(prefixIndex+prefix.length());
            chop(dirname, "/");
            if (dirname.startsWith("/")) dirname = dirname.substring(1);
        }
        return this.prefix + "/" + dirname;
    }

}
