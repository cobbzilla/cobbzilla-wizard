package org.cobbzilla.wizard;

import org.cobbzilla.util.io.TempDir;

import java.io.File;

import static org.cobbzilla.util.io.Decompressors.unroll;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

public class Unroll {

    public static TempDir unrollOrInvalid (File file, String err) {
        try { return unroll(file); } catch (Exception e) { throw invalidEx(err); }
    }

}
