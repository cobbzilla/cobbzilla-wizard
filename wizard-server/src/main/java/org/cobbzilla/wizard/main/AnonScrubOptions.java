package org.cobbzilla.wizard.main;

import lombok.Getter;
import lombok.Setter;
import org.cobbzilla.util.string.StringUtil;
import org.cobbzilla.wizard.model.anon.AnonTable;
import org.cobbzilla.wizard.model.anonymize.AnonymizeConfig;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.function.Function.identity;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.io.FileUtil.toStringOrDie;
import static org.cobbzilla.util.json.JsonUtil.FULL_MAPPER_ALLOW_COMMENTS;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.wizard.model.anonymize.AnonymizeConfig.disableTransformations;

public abstract class AnonScrubOptions extends DbMainOptions {

    protected abstract List<String> getPackagesList();

    public static final String USAGE_SCRUB_FILE = "JSON file containing an array of AnonTable objects, determines what is scrubbed. Optional, overrides code-defined settings";
    public static final String OPT_SCRUB_FILE = "-s";
    public static final String LONGOPT_SCRUB_FILE= "--scrub-file";
    @Option(name=OPT_SCRUB_FILE, aliases=LONGOPT_SCRUB_FILE, usage=USAGE_SCRUB_FILE)
    @Getter @Setter private File scrubFile = null;

    public boolean hasScrubFile () { return scrubFile != null; }

    public List<AnonTable> getScrubs() {
        if (isNoTransform()) {
            return disableTransformations(getAllScrubs());
        } else {
            return getAllScrubs();
        }
    }

    private List<AnonTable> getAllScrubs() {

        final List<AnonTable> anonTables = AnonymizeConfig.createAnonTables(getPackagesList());

        if (hasScrubFile()) {
            final String json = toStringOrDie(scrubFile);
            final AnonTable[] tables = json(json, AnonTable[].class, FULL_MAPPER_ALLOW_COMMENTS);
            final Map<String, AnonTable> tableMap = anonTables.stream().collect(Collectors.toMap(AnonTable::getTable, identity()));
            for (AnonTable t : tables) {
                tableMap.get(t.getTable()).merge(t);
            }
        }

        final List<String> tableNamesToAnonymize = getTablesList();
        if (empty(tableNamesToAnonymize)) return anonTables;

        final List<AnonTable> tablesToAnonymize = new ArrayList<>();
        for (AnonTable t : anonTables) {
            if (shouldAnonymizeTable(t.getTable())) tablesToAnonymize.add(t);
        }

        if (tablesToAnonymize.size() != getTablesList().size()) {
            tableNamesToAnonymize.removeIf(t -> tablesToAnonymize.stream().anyMatch(a -> a.getTable().equals(t)));
            return die("getScrubs: tables specified via "+OPT_TABLES+"/"+LONGOPT_TABLES+" do not exist:\n"+StringUtil.toString(tableNamesToAnonymize, "\n"));
        }

        return tablesToAnonymize;
    }

    public static final String USAGE_TABLES = "Only anonymize these tables. Use a comma-separated list with no spaces";
    public static final String OPT_TABLES = "-t";
    public static final String LONGOPT_TABLES= "--tables";
    @Option(name=OPT_TABLES, aliases=LONGOPT_TABLES, usage=USAGE_TABLES)
    @Getter @Setter private String tables = null;

    public boolean shouldAnonymizeTable (String table) { return empty(tables) || getTablesList().contains(table);
    }

    public List<String> getTablesList() { return empty(tables) ? null : StringUtil.split(tables, ", "); }

    public static final String USAGE_JSON_DUMP = "Dump JSON representation of anonymization settings intsead of applying them";
    public static final String OPT_JSON_DUMP = "-d";
    public static final String LONGOPT_JSON_DUMP= "--dump-json";
    @Option(name=OPT_JSON_DUMP, aliases=LONGOPT_JSON_DUMP, usage=USAGE_JSON_DUMP)
    @Getter @Setter private boolean dumpJson = false;

    public static final String USAGE_NO_XFORM = "Do not apply any field transformations -- treat all columns as 'passthru'";
    public static final String OPT_NO_XFORM = "-x";
    public static final String LONGOPT_NO_XFORM= "--no-transform";
    @Option(name=OPT_NO_XFORM, aliases=LONGOPT_NO_XFORM, usage=USAGE_NO_XFORM)
    @Getter @Setter private boolean noTransform = false;

}
