package org.cobbzilla.wizard.main;

import lombok.Cleanup;
import org.cobbzilla.util.main.BaseMain;

import java.sql.Connection;
import java.sql.PreparedStatement;

import static org.cobbzilla.util.io.FileUtil.abs;

public class CsvDumpMain<OPT extends CsvDumpOptions> extends BaseMain<OPT> {

    private static final String SUBST_TABLES = "@@TABLES@@";

    // adapted from: https://stackoverflow.com/a/37210706/1251543
    public static final String CSV_DUMP_FUNCTION
            = "CREATE OR REPLACE FUNCTION db_to_csv(path TEXT) RETURNS void AS $$\n" +
            "declare\n" +
            "   tables RECORD;\n" +
            "   statement TEXT;\n" +
            "begin\n" +
            "FOR tables IN \n" +
            "   SELECT (table_schema || '.' || table_name) AS schema_table\n" +
            "   FROM information_schema.tables t INNER JOIN information_schema.schemata s \n" +
            "   ON s.schema_name = t.table_schema \n" +
            "   WHERE t.table_schema NOT IN ('pg_catalog', 'information_schema', 'configuration')\n" +
            "   AND t.table_type NOT IN ('VIEW')\n" +
            "   " + SUBST_TABLES +
            "   ORDER BY schema_table\n" +
            "LOOP\n" +
            "   statement := 'COPY ' || tables.schema_table || ' TO ''' || path || '/' || tables.schema_table || '.csv' ||''' DELIMITER '';'' CSV HEADER';\n" +
            "   EXECUTE statement;\n" +
            "END LOOP;\n" +
            "return;  \n" +
            "end;\n" +
            "$$ LANGUAGE plpgsql;";

    @Override protected void run() throws Exception {
        final OPT options = getOptions();

        final String tableClause = options.hasTables() ? " AND t.table_name IN (" + options.getTableValues() + ")" : "";
        final String funcSql = CSV_DUMP_FUNCTION.replace(SUBST_TABLES, tableClause);
        @Cleanup final Connection c = options.getDatabaseConfiguration().getDatabase().getConnection();
        @Cleanup final PreparedStatement fs = c.prepareStatement(funcSql);
        fs.execute();
        @Cleanup final PreparedStatement ds = c.prepareStatement("SELECT db_to_csv('"+abs(options.getOutputDir())+"');");
        ds.execute();
    }

}
