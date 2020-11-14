package org.cobbzilla.wizard.model;

import lombok.*;
import lombok.extern.slf4j.Slf4j;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.Transient;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.io.FileFilter;
import java.io.Serializable;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.wizard.model.BasicConstraintConstants.*;

@Embeddable @EqualsAndHashCode(callSuper=false)
@NoArgsConstructor @AllArgsConstructor @Slf4j
public class SemanticVersion implements Comparable<SemanticVersion>, Serializable {

    public static final String SEMANTIC_VERSION_RE = "(\\d+)\\.(\\d+)\\.(\\d+)(\\.(\\d+))?";
    public static final String VERSION_REGEXP = "^" + SEMANTIC_VERSION_RE + "$";
    public static final Pattern VERSION_PATTERN = Pattern.compile(VERSION_REGEXP);

    public static final FileFilter DIR_FILTER = pathname -> pathname.isDirectory() && SemanticVersion.isValid(pathname.getName());

    public static final Comparator<SemanticVersion> COMPARE_LATEST_FIRST = Comparator.reverseOrder();

    public SemanticVersion(int major, int minor, int patch) { this(major, minor, patch, null); }

    /**
     * Is v2 newer than v1?
     * @param v1 first version
     * @param v2 second version
     * @return true if v2 is newer than v1
     */
    public static boolean isNewerVersion(String v1, String v2) {
        if (empty(v1) || empty(v2)) return false;
        v1 = v1.replaceAll("[^.\\d]+", "");
        v2 = v2.replaceAll("[^.\\d]+", "");
        try {
            return new SemanticVersion(v1).compareTo(new SemanticVersion(v2)) < 0;
        } catch (Exception e) {
            log.error("isNewer("+v1+", "+v2+"): returning false due to "+shortError(e));
            return false;
        }
    }

    public SemanticVersion (String version) {
        if (empty(version)) die("empty version string");
        final Matcher matcher = VERSION_PATTERN.matcher(version);
        if (!matcher.find()) die("Invalid version: " + version);
        setMajor(Integer.parseInt(matcher.group(1)));
        setMinor(Integer.parseInt(matcher.group(2)));
        setPatch(Integer.parseInt(matcher.group(3)));
        final String buildNumber = matcher.group(5);
        if (!empty(buildNumber)) setBuild(Integer.valueOf(buildNumber));
    }

    @Max(value=SV_VERSION_MAX, message=SV_MAJOR_TOO_LARGE)
    @Min(value=SV_VERSION_MIN, message=SV_MAJOR_TOO_SMALL)
    @Column(name="major_version", length=SV_VERSION_MAXLEN)
    @Getter @Setter private int major = 1;
    public SemanticVersion incrementMajor () { return new SemanticVersion(major+1, 0, 0); }

    @Max(value=SV_VERSION_MAX, message=SV_MINOR_TOO_LARGE)
    @Min(value=SV_VERSION_MIN, message=SV_MINOR_TOO_SMALL)
    @Column(name="minor_version", length=SV_VERSION_MAXLEN)
    @Getter @Setter private int minor = 0;
    public SemanticVersion incrementMinor () { return new SemanticVersion(major, minor+1, 0); }

    @Max(value=SV_VERSION_MAX, message=SV_PATCH_TOO_LARGE)
    @Min(value=SV_VERSION_MIN, message=SV_PATCH_TOO_SMALL)
    @Column(name="patch_version", length=SV_VERSION_MAXLEN)
    @Getter @Setter private int patch = 0;
    public SemanticVersion incrementPatch () { return new SemanticVersion(major, minor, patch+1); }

    public static SemanticVersion incrementPatch(SemanticVersion other) {
        return new SemanticVersion(other.getMajor(), other.getMinor(), other.getPatch()+1);
    }

    @Max(value=SV_VERSION_MAX, message=SV_BUILD_TOO_LARGE)
    @Min(value=SV_VERSION_MIN, message=SV_BUILD_TOO_SMALL)
    @Transient @Getter @Setter private Integer build = null;

    public static boolean isValid (String version) { return VERSION_PATTERN.matcher(version).find(); }

    @Override public String toString () { return major + "." + minor + "." + patch + (build == null ? "" : "."+build); }

    @Override public int compareTo(SemanticVersion other) {
        if (other == null) throw new IllegalArgumentException("compareTo: argument was null");
        int diff;
        diff = major - other.major; if (diff != 0) return diff;
        diff = minor - other.minor; if (diff != 0) return diff;
        diff = patch - other.patch; if (diff != 0) return diff;
        if (build != null && other.build != null) {
            diff = build - other.build;
            if (diff != 0) return diff;
        }
        return 0;
    }

}
