package org.cobbzilla.wizard.model;

import lombok.*;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.io.File;
import java.io.FileFilter;
import java.io.Serializable;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.wizard.model.BasicConstraintConstants.*;

@Embeddable @EqualsAndHashCode(callSuper=false)
@NoArgsConstructor @AllArgsConstructor
public class SemanticVersion implements Comparable<SemanticVersion>, Serializable {

    public static final String SEMANTIC_VERSION_RE = "(\\d+)\\.(\\d+)\\.(\\d+)";
    public static final String VERSION_REGEXP = "^" + SEMANTIC_VERSION_RE + "$";
    public static final Pattern VERSION_PATTERN = Pattern.compile(VERSION_REGEXP);

    public static final FileFilter DIR_FILTER = new FileFilter() {
        @Override public boolean accept(File pathname) {
            return pathname.isDirectory() && SemanticVersion.isValid(pathname.getName());
        }
    };

    public static final Comparator<SemanticVersion> COMPARE_LATEST_FIRST = new Comparator<SemanticVersion>() {
        @Override public int compare(SemanticVersion o1, SemanticVersion o2) {
            return o2.compareTo(o1);
        }
    };

    public SemanticVersion (String version) {
        if (empty(version)) die("empty version string");
        final Matcher matcher = VERSION_PATTERN.matcher(version);
        if (!matcher.find()) die("Invalid version: " + version);
        setMajor(Integer.parseInt(matcher.group(1)));
        setMinor(Integer.parseInt(matcher.group(2)));
        setPatch(Integer.parseInt(matcher.group(3)));
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

    public static boolean isValid (String version) { return VERSION_PATTERN.matcher(version).find(); }

    @Override public String toString () { return major + "." + minor + "." + patch; }

    @Override public int compareTo(SemanticVersion other) {
        if (other == null) throw new IllegalArgumentException("compareTo: argument was null");
        int diff;
        diff = major - other.major; if (diff != 0) return diff;
        diff = minor - other.minor; if (diff != 0) return diff;
        diff = patch - other.patch; if (diff != 0) return diff;
        return 0;
    }

}
