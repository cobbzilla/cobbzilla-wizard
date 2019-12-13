package org.cobbzilla.wizard.model.entityconfig;

import lombok.AllArgsConstructor;

import java.io.File;

import static org.cobbzilla.util.io.FileUtil.abs;
import static org.cobbzilla.util.io.FileUtil.toStringOrDie;

@AllArgsConstructor
public class ManifestFileResolver implements ModelManifestResolver {

    private final File baseDir;

    public ManifestFileResolver () { this(new File(System.getProperty("user.dir"))); }

    @Override public ModelManifestResolver subResolver(String dirname) {
        return new ManifestFileResolver(new File(abs(baseDir)+ "/"+ dirname));
    }

    @Override public String jsonAtPath(String path) {
        return toStringOrDie(new File(abs(baseDir)+"/"+path));
    }
}
