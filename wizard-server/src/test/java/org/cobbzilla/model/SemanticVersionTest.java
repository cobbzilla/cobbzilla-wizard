package org.cobbzilla.model;

import org.apache.bcel.generic.NEW;
import org.apache.commons.lang3.StringUtils;
import org.cobbzilla.wizard.model.SemanticVersion;
import org.junit.Test;

import static org.cobbzilla.wizard.model.SemanticVersion.isNewerVersion;
import static org.junit.Assert.*;

public class SemanticVersionTest {

    public static final Object[][] VALID_VERSIONS = {
            { "0.0.0", new SemanticVersion(0, 0, 0) },
            { "1.2.3" , new SemanticVersion(1, 2, 3) },
            { "12.1.0", new SemanticVersion(12, 1, 0) },
            { "1.2.3.4", new SemanticVersion(1, 2, 3, 4) },
    };

    @Test public void testValidVersions () {
        for (Object[] test : VALID_VERSIONS) {
            final SemanticVersion v = new SemanticVersion(test[0].toString());
            final SemanticVersion expected = (SemanticVersion) test[1];
            assertEquals(expected.getMajor(), v.getMajor());
            assertEquals(expected.getMinor(), v.getMinor());
            assertEquals(expected.getPatch(), v.getPatch());
            if (StringUtils.countMatches(test[0].toString(), '.') == 3) {
                assertEquals(expected.getBuild(), v.getBuild());
            } else {
                assertNull(expected.getBuild());
                assertNull(v.getBuild());
            }
            assertEquals(expected, v);
        }
    }

    public static final String[] INVALID_VERSIONS = {
            "", ".", "0.", "1.0", "0.0.", "a.1.0", "1. 0 . 5", "1.0.0.x"
    };

    @Test public void testInvalidVersions () {
        for (String test : INVALID_VERSIONS) {
            try {
                final SemanticVersion v = new SemanticVersion(test);
                fail("expected version to be invalid: "+test+", got version="+v);
            } catch (Exception ignored) {}
        }
    }

    public static final String[][] NEWER_COMPARISONS = {
            {"1.0.1", "1.0.0"},
            {"1.0.1.1", "1.0.0"},
            {"1.2.3", "1.2.2"},
    };

    @Test public void testNewerVersions () {
        for (String[] versions : NEWER_COMPARISONS) {
            final String v1 = versions[0];
            final String v2 = versions[1];
            assertTrue("expected "+v1+ " to be considered newer than "+v2, isNewerVersion(v2, v1));
        }
    }

}
