package org.cobbzilla.model;

import org.cobbzilla.wizard.model.SemanticVersion;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class SemanticVersionTest {

    public static final Object[][] VALID_VERSIONS = {
            { "0.0.0", new SemanticVersion(0, 0, 0) },
            { "1.2.3" , new SemanticVersion(1, 2, 3) },
            { "12.1.0", new SemanticVersion(12, 1, 0) },
    };

    @Test
    public void testValidVersions () {
        for (Object[] test : VALID_VERSIONS) {
            final SemanticVersion v = new SemanticVersion(test[0].toString());
            final SemanticVersion expected = (SemanticVersion) test[1];
            assertEquals(expected.getMajor(), v.getMajor());
            assertEquals(expected.getMinor(), v.getMinor());
            assertEquals(expected.getPatch(), v.getPatch());
            assertEquals(expected, v);
        }
    }

    public static final String[] INVALID_VERSIONS = {
            "", ".", "0.", "1.0", "0.0.", "a.1.0", "1. 0 . 5", "1.0.0.x"
    };

    @Test
    public void testInvalidVersions () {
        for (String test : INVALID_VERSIONS) {
            try {
                final SemanticVersion v = new SemanticVersion(test);
                fail("expected version to be invalid: "+test+", got version="+v);
            } catch (Exception ignored) {}
        }
    }

}
