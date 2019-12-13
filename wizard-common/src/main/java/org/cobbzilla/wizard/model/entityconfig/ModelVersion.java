package org.cobbzilla.wizard.model.entityconfig;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import lombok.experimental.Accessors;
import org.cobbzilla.util.collection.ArrayUtil;
import org.cobbzilla.util.io.FileUtil;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.IdentifiableBase;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;
import javax.persistence.Transient;
import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.io.FileUtil.abs;
import static org.cobbzilla.util.io.FileUtil.listDirs;
import static org.cobbzilla.util.json.JsonUtil.FULL_MAPPER_ALLOW_COMMENTS;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;
import static org.cobbzilla.util.security.ShaUtil.sha256_hex;

@MappedSuperclass @NoArgsConstructor @AllArgsConstructor @Accessors(chain=true)
@ToString(of={"version", "hash", "description"})
public class ModelVersion extends IdentifiableBase {

    public static final String ERR_MODEL_VERSION_ALREADY_SUCCESSFULLY_APPLIED = "err.modelVersion.alreadySuccessfullyApplied";
    public static final String ERR_MODEL_VERSION_INVALID = "err.modelVersion.invalid";

    public static final String CURRENT = "current";

    public static final String[] UPDATE_FIELDS = { "description", "hash", "installedBy", "installedOn", "executionTime", "success" };
    public static final String[] CREATE_FIELDS = ArrayUtil.append(UPDATE_FIELDS, "version");

    public ModelVersion (ModelVersion other) { copy(this, other, CREATE_FIELDS); }
    @Override public Identifiable update(Identifiable other) { copy(this, other, UPDATE_FIELDS); return this; }

    public static final String VERSION_DIR_REGEX = "^V(\\d+)__(.+)$";
    public static final Pattern VERSION_DIR_PATTERN = Pattern.compile(VERSION_DIR_REGEX);

    public static final String MODEL_FILE_REGEX = "^([A-Za-z]+)(_.+)?$";
    public static final Pattern MODEL_FILE_PATTERN = Pattern.compile(MODEL_FILE_REGEX);

    public static boolean isMigrationDir (File dir) {
        return VERSION_DIR_PATTERN.matcher(dir.getName()).find();
    }
    public static int parseVersion (File f) { return Integer.parseInt(VERSION_DIR_PATTERN.matcher(f.getName()).group(1)); }

    public static Collection<ModelVersion> fromBaseDir(File baseDir) {
        // find migration dirs
        final File[] dirs = listDirs(baseDir, VERSION_DIR_REGEX);

        // build/sort ModelVersion objects
        final Set<ModelVersion> sorted = new TreeSet<>(new Comparator<ModelVersion>() {
            @Override public int compare(ModelVersion mv1, ModelVersion mv2) {
                return Integer.compare(mv1.getVersion(), mv2.getVersion());
            }
        });
        for (File dir : dirs) sorted.add(new ModelVersion(dir));

        return sorted;
    }

    public static String getEntityType (String path) {
        final Matcher m = MODEL_FILE_PATTERN.matcher(path);
        return m.find() ? m.group(1) : null;
    }

    public ModelVersion (File dir) {
        final String errPrefix = "ModelVersion("+abs(dir)+"): ";
        final Matcher m = VERSION_DIR_PATTERN.matcher(dir.getName());
        if (!m.find()) die(errPrefix+"invalid name (does not match regex "+VERSION_DIR_REGEX+"): "+dir.getName());
        setVersion(Integer.parseInt(m.group(1)));
        setDescription(m.group(2).replace("_", " "));

        final File manifestFile = new File(dir, "manifest.json");
        if (!manifestFile.exists() || !manifestFile.canRead()) die(errPrefix+" manifest file does not exist or is unreadable: "+abs(manifestFile));
        final String[] migrationFiles = json(FileUtil.toStringOrDie(manifestFile), String[].class, FULL_MAPPER_ALLOW_COMMENTS);
        if (empty(migrationFiles)) die(errPrefix+"manifest had no model files");

        final StringBuilder b = new StringBuilder();
        final LinkedHashMap<String, String> migrations = new LinkedHashMap<>();
        for (String file : migrationFiles) {

            final String entityType = getEntityType(file);
            if (entityType == null) die(errPrefix+"invalid model file (no entity type could be determined): "+file);

            final File f = new File(dir, file + ".json");
            if (!f.exists()) die(errPrefix+"model file does not exist: "+abs(f));

            final String singleFileJson = FileUtil.toStringOrDie(f);
            migrations.put(file, singleFileJson);
            b.append(singleFileJson);
        }
        setHash(sha256_hex(b.toString()));
        setModels(migrations);
    }

    @Column(nullable=false, updatable=false, unique=true)
    @Getter @Setter private int version;

    @Column(nullable=false, length=1024)
    @Getter @Setter private String description;

    public String shortString() { return getVersion()+"/"+getDescription(); }

    @Transient @JsonIgnore @Getter @Setter private LinkedHashMap<String, String> models;

    @Column(nullable=false, length=200)
    @Getter @Setter private String hash;

    @Column(nullable=false, length=200)
    @Getter @Setter private String installedBy;

    @Column(nullable=false)
    @Getter @Setter private long installedOn;

    @Column(nullable=false)
    @Getter @Setter private long executionTime;

    @Column(nullable=false)
    @Getter @Setter private boolean success;

}
