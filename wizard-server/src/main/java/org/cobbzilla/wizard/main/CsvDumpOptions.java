package org.cobbzilla.wizard.main;

import lombok.Getter;
import lombok.Setter;
import org.cobbzilla.util.string.StringUtil;
import org.kohsuke.args4j.Option;

import java.io.File;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

public abstract class CsvDumpOptions extends DbMainOptions {

    public static final String USAGE_OUTPUT_DIR = "Output directory. Default is current directory";
    public static final String OPT_OUTPUT_DIR = "-o";
    public static final String LONGOPT_OUTPUT_DIR= "--output-dir";
    @Option(name=OPT_OUTPUT_DIR, aliases=LONGOPT_OUTPUT_DIR, usage=USAGE_OUTPUT_DIR)
    @Getter @Setter private File outputDir = new File(System.getProperty("user.dir"));

    public static final String USAGE_TABLES = "Output directory. Default is current directory";
    public static final String OPT_TABLES = "-o";
    public static final String LONGOPT_TABLES= "--output-dir";
    @Option(name=OPT_TABLES, aliases=LONGOPT_TABLES, usage=USAGE_TABLES)
    @Getter @Setter private String tables;

    public boolean hasTables () { return !empty(tables); }

    public String getTableValues() {
        final StringBuilder b = new StringBuilder();
        for (String table : StringUtil.split(tables, ", ")) {
            if (b.length() > 0) b.append(", ");
            b.append("'").append(table).append("'");
        }
        return b.toString();
    }

}
