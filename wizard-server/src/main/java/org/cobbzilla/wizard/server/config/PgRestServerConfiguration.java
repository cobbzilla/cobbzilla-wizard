package org.cobbzilla.wizard.server.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Cleanup;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.CommandLine;
import org.cobbzilla.util.collection.ArrayUtil;
import org.cobbzilla.util.io.FileUtil;
import org.cobbzilla.util.jdbc.DbDumpMode;
import org.cobbzilla.util.jdbc.ResultSetBean;
import org.cobbzilla.util.jdbc.UncheckedSqlException;
import org.cobbzilla.util.string.StringUtil;
import org.cobbzilla.util.system.Command;
import org.cobbzilla.util.system.CommandResult;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.entityconfig.EntityReferences;
import org.springframework.context.annotation.Bean;

import javax.persistence.Transient;
import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.cobbzilla.util.collection.ArrayUtil.EMPTY_OBJECT_ARRAY;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.http.URIUtil.getHost;
import static org.cobbzilla.util.http.URIUtil.getPort;
import static org.cobbzilla.util.io.FileUtil.*;
import static org.cobbzilla.util.string.StringUtil.camelCaseToSnakeCase;
import static org.cobbzilla.util.system.CommandShell.exec;
import static org.cobbzilla.util.system.CommandShell.execScript;

@Slf4j
public class PgRestServerConfiguration extends RestServerConfiguration implements HasDatabaseConfiguration {

    private DatabaseConfiguration database;
    @Override @Bean public DatabaseConfiguration getDatabase() { return database; }
    @Override public void setDatabase(DatabaseConfiguration config) { this.database = config; }

    public ResultSetBean execSql(String sql) { return execSql(sql, EMPTY_OBJECT_ARRAY); }
    public ResultSetBean execSql(String sql, Object[] args) {
        try {
            @Cleanup Connection conn = getDatabase().getConnection();
            return execSql(conn, sql, args);

        } catch (SQLException e) {
            throw new UncheckedSqlException(e);

        } catch (UncheckedSqlException e) {
            throw e;

        } catch (Exception e) {
            return die("Exception: "+e, e);
        }
    }

    @Transient @JsonIgnore
    @Getter @Setter private Boolean execSqlStrictStrings = null;

    public ResultSetBean execSql(Connection conn, String sql, Object[] args) {
        try {
            @Cleanup PreparedStatement ps = conn.prepareStatement(sql);
            if (args != null) {
                int i = 1;
                for (Object o : args) {
                    if (o == null) {
                        die("null arguments not supported. null value at parameter index=" + i + ", sql=" + sql);
                    }
                    if (o instanceof String) {
                        if (execSqlStrictStrings == null || execSqlStrictStrings == false) {
                            if (o.toString().equalsIgnoreCase(Boolean.TRUE.toString())) {
                                ps.setBoolean(i++, true);
                            } else if (o.toString().equalsIgnoreCase(Boolean.FALSE.toString())) {
                                ps.setBoolean(i++, false);
                            } else {
                                ps.setString(i++, (String) o);
                            }
                        } else {
                            ps.setString(i++, (String) o);
                        }
                    } else if (o instanceof Long) {
                        ps.setLong(i++, (Long) o);
                    } else if (o instanceof Integer) {
                        ps.setInt(i++, (Integer) o);
                    } else if (o instanceof Boolean) {
                        ps.setBoolean(i++, (Boolean) o);
                    } else if (o instanceof Object[]) {
                        final Array arrayParam = conn.createArrayOf("varchar", (Object[]) o);
                        ps.setArray(i++, arrayParam);
                    } else {
                        die("unsupported argument type: " + o.getClass().getName());
                    }
                }
            }

            final boolean isQuery = sql.toLowerCase().trim().startsWith("select");
            if (isQuery) {
                @Cleanup ResultSet rs = ps.executeQuery();
                log.info("execSql (query): "+sql);
                return new ResultSetBean(rs);
            }

            ps.executeUpdate();
            log.info("execSql (update): "+sql);
            return ResultSetBean.EMPTY;

        } catch (SQLException e) {
            throw new UncheckedSqlException(e);

        } catch (Exception e) {
            return die("Exception: "+e, e);
        }
    }

    public int rowCount(String table) throws SQLException {
        return execSql("select count(*) from " + table).count();
    }

    public int rowCountOrZero(String table) {
        try { return rowCount(table); } catch (Exception e) {
            log.warn("rowCountOrZero (returning 0): "+e);
            return 0;
        }
    }

    public String[] allTables() {
        final ResultSetBean rs = execSql("SELECT tablename FROM pg_tables WHERE schemaname = 'public'");
        final String[] tables = new String[rs.rowCount()];
        final ArrayList<Map<String, Object>> rows = rs.getRows();
        for (int i=0; i<tables.length; i++) {
            tables[i] = rows.get(i).get("tablename").toString();
        }
        return tables;
    }

    @Getter @Setter private String pgServerDir;

    public String[] pgCommand() { return pgCommand("psql"); }
    public String[] pgOptions() { return pgCommand(""); }
    public String[] pgOptions(String dbName) { return pgCommand("", dbName); }

    public boolean dbExists() {
        try {
            execSql("select 1");
            return true;
        } catch (Exception e) {
            log.warn("dbExists: "+shortErrorString(e));
            return false;
        }
    }

    public String[] pgCommand(String command)            { return pgCommand(command, null, null); }
    public String[] pgCommand(String command, String db) { return pgCommand(command, db, null); }

    public String[] pgCommand(String command, String db, String user) {
        final String dbUser = !empty(user) ? user : getDatabase().getUser();
        final String dbUrl = getDatabase().getUrl();

        // here we assume URL is in the form 'jdbc:{driver}://{host}:{port}/{db_name}'
        final int colonPos = dbUrl.indexOf(":");
        final String host = getHost(dbUrl.substring(colonPos+1));
        final int port = getPort(dbUrl.substring(colonPos+1));
        final int qPos = dbUrl.indexOf("?");
        final String dbName = !empty(db) ? db : qPos == -1 ? dbUrl.substring(dbUrl.lastIndexOf('/')+1) : dbUrl.substring(dbUrl.lastIndexOf("/")+1, qPos);

        final String[] options = new String[] { "-h", host, "-p", String.valueOf(port), "-U", dbUser, dbName };
        if (empty(command)) return options;

        final String pgServerDir = getPgServerDir();
        return empty(pgServerDir)
                ? ArrayUtil.concat(new String[] { command }, options)
                : ArrayUtil.concat(new String[] {abs(new File(pgServerDir + sep + "bin" + sep + command))}, options);
    }

    public String pgCommandString(String command) { return pgCommandString(command, null, null); }

    public String pgCommandString(String command, String db, String user) {
        command = ArrayUtil.arrayToString(pgCommand(command, db, user), " ", "", false);
        final File pgPassFile = getPgPassFile();
        return pgPassFile != null && pgPassFile.exists()
                ? "PGPASSWORD=\"$(cat " + abs(pgPassFile) + ")\" " + command
                : command;
    }

    @JsonIgnore public File getPgPassFile() { return null; }

    public CommandLine pgCommandLine(String command) {
        if (empty(command)) return die("pgCommandLine: no command provided");
        final String pgServerDir = getPgServerDir();
        if (!empty(pgServerDir)) command = abs(new File(pgServerDir + sep + "bin" + sep + command));
        return new CommandLine(command).addArguments(pgOptions());
    }

    public Map<String, String> pgEnv() {
        String dbPass = getDatabase().getPassword();
        if (empty(dbPass)) dbPass = "";

        final Map<String, String> env = new HashMap<>();
        env.putAll(getEnvironment());
        env.put("PGPASSWORD", dbPass);
        String path = env.get("PATH");
        if (path == null) {
            path = "/bin:/usr/bin:/usr/local/bin";
        } else {
            path += ":/usr/local/bin";
        }
        env.put("PATH", path);

        return env;
    }

    private static final Pattern DROP_PATTERN = Pattern.compile("^drop ", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    public void execSqlCommands(String[] sqlCommands) { Arrays.stream(sqlCommands).forEach(this::execSql); }

    public void execSqlCommands(String sqlCommands) {
        for (String sql : StringUtil.split(sqlCommands, ";")) {
            try {
                execSql(sql);
            } catch (Exception e) {
                if (DROP_PATTERN.matcher(sql).find()) {
                    log.info("execSqlCommands ("+sql+"): " + e.getMessage());
                } else {
                    log.warn("execSqlCommands ("+sql+"): " + e.getMessage());
                }
            }
        }
    }

    public String[] sqlColumns(String relation) {
        try {
            @Cleanup final Connection conn = getDatabase().getConnection();
            @Cleanup final PreparedStatement ps = conn.prepareStatement("SELECT * FROM " + relation + " WHERE 1=0");
            @Cleanup final ResultSet rs = ps.executeQuery();
            final ResultSetMetaData rsmd = rs.getMetaData();
            final String[] columns = new String[rsmd.getColumnCount()];
            for (int i = 0; i < columns.length; i++) columns[i] = rsmd.getColumnName(i + 1);
            return columns;
        } catch (Exception e) {
            return die("sqlColumns("+relation+"): "+e, e);
        }
    }

    public File pgDump() { return pgDump(temp("pgDump-out", ".sql")); }

    public File pgDump(File file) { return pgDump(file, null); }

    public File pgDump(File file, DbDumpMode dumpMode) { return pgDump(file, dumpMode, file.getName().endsWith(".gz")); }

    public File pgDump(File file, DbDumpMode dumpMode, boolean gzip) {
        final File temp = temp("pgRestore-out", ".sql" + (gzip ? ".gz" : ""));
        final String dumpOptions;
        if (dumpMode == null) dumpMode = DbDumpMode.all;
        switch (dumpMode) {
            case all: dumpOptions = "--inserts"; break;
            case schema: dumpOptions = "--schema-only"; break;
            case data: dumpOptions = "--data-only --inserts"; break;
            case pre_data: dumpOptions = "--section=pre-data"; break;
            case post_data: dumpOptions = "--section=post-data"; break;
            default: return die("pgDump: invalid dumpMode: "+dumpMode);
        }
        return retry(() -> {
            final String output = execScript(pgCommandString("pg_dump") + " " + dumpOptions + (gzip ? " | gzip" : "") + " > " + abs(temp) + " || exit 1", pgEnv());
            if (output.contains("ERROR")) die("pgDump: error dumping DB:\n" + output);
            if (!temp.renameTo(file)) {
                log.warn("pgDump: error renaming file, trying copy");
                copyFile(temp, file);
                if (!temp.delete()) log.warn("pgDump: error deleting temp file: " + abs(temp));
            }
            log.info("pgDump: dumped DB to snapshot: " + abs(file));
            return file;
        }, MAX_DUMP_TRIES);
    }

    public File pgRestore(File file) {
        return retry(() -> {
            final CommandResult result = exec(new Command(pgCommandLine("psql"))
                    .setInput(FileUtil.toString(file))
                    .setEnv(pgEnv()));
            //if (result.getStderr().contains("ERROR")) die("pgRestore: error restoring DB:\n"+result.getStderr());
            log.info("pgRestore: restored DB from snapshot: " + abs(file));
            return file;
        }, MAX_DUMP_TRIES);
    }

    @Getter(lazy=true) private final List<Class<? extends Identifiable>> entityClasses = initEntityClasses();
    private List<Class<? extends Identifiable>> initEntityClasses() {
        return new EntityReferences()
                .setPackages(getDatabase().getHibernate().getEntityPackages())
                .dependencyOrder();
    }

    @Getter(lazy=true) private final List<Class<? extends Identifiable>> entityClassesReverse = initEntityClassesReverse();
    private List<Class<? extends Identifiable>> initEntityClassesReverse() {
        final ArrayList<Class<? extends Identifiable>> reversed = new ArrayList<>(getEntityClasses());
        Collections.reverse(reversed);
        return reversed;
    }

    @Getter(lazy=true) private final List<String> tableNames = initTableNames();
    private List<String> initTableNames() {
        return getEntityClasses().stream()
                .map(c -> camelCaseToSnakeCase(c.getSimpleName()))
                .collect(Collectors.toList());
    }

    public String[] getSqlConstraints() { return getSqlConstraints(true); }

    public String[] getSqlConstraints(boolean includeIndexes) {
        return new EntityReferences()
                .setPackages(getDatabase().getHibernate().getEntityPackages())
                .generateConstraintSql(includeIndexes).toArray(new String[0]);
    }

}
