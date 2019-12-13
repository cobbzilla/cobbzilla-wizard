package org.cobbzilla.wizard.server.config;

import lombok.Getter;
import lombok.Setter;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

public class StaticHttpConfiguration {

    @Getter @Setter private Map<String, String> utilPaths = new HashMap<>();
    @Getter @Setter private String baseUri;
    @Getter @Setter private String assetRoot;
    @Getter @Setter private File localOverride;
    @Getter @Setter private String singlePageApp;
    @Getter @Setter private String resourceRoot;

    public boolean hasAssetRoot() { return !empty(assetRoot); }
    public boolean hasLocalOverride() { return localOverride != null; }

    @Getter @Setter private Map<String, Map<String, String>> substitutions = new HashMap<>();
    public Map<String, String> getSubstitutions(String resourcePath) { return substitutions.get(resourcePath); }

    public static final File DEFAULT_SUBST_CACHE_DIR = new File(System.getProperty("java.io.tmpdir"));
    public File getSubstitutionCacheDir () { return DEFAULT_SUBST_CACHE_DIR; }
    public String getSubstitutionDelimiter () { return "@@@"; }

}
