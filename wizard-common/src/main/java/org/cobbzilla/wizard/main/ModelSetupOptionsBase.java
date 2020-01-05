package org.cobbzilla.wizard.main;

import lombok.Getter;
import lombok.Setter;
import org.cobbzilla.wizard.model.entityconfig.ModelSetup;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.io.InputStream;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

public abstract class ModelSetupOptionsBase extends MainApiOptionsBase {

    public static final String USAGE_ENTITY_CONFIG_URL = "set URL endpoint for entity-configs";
    public static final String OPT_ENTITY_CONFIG_URL = "-e";
    public static final String LONGOPT_ENTITY_CONFIG_URL= "--entity-config-url";
    @Option(name=OPT_ENTITY_CONFIG_URL, aliases=LONGOPT_ENTITY_CONFIG_URL, usage=USAGE_ENTITY_CONFIG_URL)
    @Setter private String entityConfigUrl;

    public String getEntityConfigUrl() {
        if (!empty(entityConfigUrl)) return entityConfigUrl;
        return getDefaultEntityConfigUrl();
    }

    protected abstract String getDefaultEntityConfigUrl();

    public static final String USAGE_MANIFEST = "use this JSON manifest to populate a variety of entities";
    public static final String OPT_MANIFEST = "-m";
    public static final String LONGOPT_MANIFEST= "--manifest";
    @Option(name=OPT_MANIFEST, aliases=LONGOPT_MANIFEST, usage=USAGE_MANIFEST)
    @Getter @Setter private File manifest;
    public boolean hasManifest () { return manifest != null; }

    public static final String USAGE_ENTITY_TYPE = "populate entities only of this type";
    public static final String OPT_ENTITY_TYPE = "-E";
    public static final String LONGOPT_ENTITY_TYPE= "--entity-type";
    @Option(name=OPT_ENTITY_TYPE, aliases=LONGOPT_ENTITY_TYPE, usage=USAGE_ENTITY_TYPE)
    @Setter private String entityType;
    public String getEntityType () { return !empty(entityType) ? entityType : infile != null ? ModelSetup.getEntityTypeFromString(infile.getName()) : (String) die("getEntityType: "+LONGOPT_ENTITY_TYPE+"/"+USAGE_ENTITY_TYPE+" must be specified when reading from stdin"); }

    public static final String USAGE_INFILE = "populate entities defined in this JSON file";
    public static final String OPT_INFILE = "-f";
    public static final String LONGOPT_INFILE= "--file";
    @Option(name=OPT_INFILE, aliases=LONGOPT_INFILE, usage=USAGE_INFILE)
    @Getter @Setter private File infile;

    public InputStream getInStream () { return inStream(getInfile()); }

    public static final String USAGE_UPDATE = "full synchronization: every entity will either be created or update";
    public static final String OPT_UPDATE = "-u";
    public static final String LONGOPT_UPDATE= "--update-all";
    @Option(name=OPT_UPDATE, aliases=LONGOPT_UPDATE, usage=USAGE_UPDATE)
    @Getter @Setter private boolean update = false;

}
