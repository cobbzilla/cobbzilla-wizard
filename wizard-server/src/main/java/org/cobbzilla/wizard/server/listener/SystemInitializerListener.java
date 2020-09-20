package org.cobbzilla.wizard.server.listener;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.jdbc.ResultSetBean;
import org.cobbzilla.wizard.cache.redis.RedisService;
import org.cobbzilla.wizard.server.RestServer;
import org.cobbzilla.wizard.server.RestServerLifecycleListenerBase;
import org.cobbzilla.wizard.server.config.PgRestServerConfiguration;

import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.network.NetworkUtil.getExternalIp;
import static org.cobbzilla.util.network.NetworkUtil.isLocalIpv4;
import static org.cobbzilla.util.string.StringUtil.camelCaseToSnakeCase;
import static org.cobbzilla.util.string.StringUtil.checkSafeShellArg;
import static org.cobbzilla.util.system.CommandShell.execScript;
import static org.cobbzilla.wizard.server.config.PgRestServerConfiguration.dbExists;
import static org.cobbzilla.wizard.server.config.PgRestServerConfiguration.dbUserExists;

@Slf4j @Accessors(chain=true)
public class SystemInitializerListener extends RestServerLifecycleListenerBase {

    public static final String PREFIX = SystemInitializerListener.class.getSimpleName() + ": ";
    public static final String SAFE_CHARS_MESSAGE = " -- this can only contain letters, numbers, spaces, tabs, and these special characters: -._/=";
    public static void invalidName(final String msg, String value) { die(PREFIX + msg + ": " + value + SAFE_CHARS_MESSAGE); }

    @Getter @Setter private boolean checkRedis = true;

    @Getter @Setter private String checkTableName = null;
    @Getter @Setter private boolean checkTable = true;
    @Getter @Setter private boolean requireExternalIp = true;

    // If no checkTableName is provided, use the table name for the first entity
    private String getCheckTableName(PgRestServerConfiguration config) {
        if (empty(checkTableName)) {
            checkTableName = camelCaseToSnakeCase(config.getEntityClasses().get(0).getSimpleName());
        }
        return checkTableName;
    }

    @Override public void beforeStart(RestServer server) {
        // first check external IP
        if (requireExternalIp) {
            final String externalIp = getExternalIp();
            if (externalIp == null || isLocalIpv4(externalIp)) {
                die(PREFIX+"Detected invalid external IP ("+externalIp+"), ensure DNS resolution is working properly on this system (perhaps check /etc/resolv.conf ?)");
            } else {
                log.info("Detected external IP: " + externalIp);
            }
        }

        final PgRestServerConfiguration config = (PgRestServerConfiguration) server.getConfiguration();
        final String db = config.getDatabase().getDatabaseName();
        final String user = config.getDatabase().getUser();
        final String password = config.getDatabase().getPassword();

        // we're going to use these in shell scripts, so ensure they are safe
        if (!checkSafeShellArg(db)) invalidName("invalid db name", db);
        if (!checkSafeShellArg(user)) invalidName("invalid db user name", user);
        if (!checkSafeShellArg(password)) invalidName(": invalid password for user '"+user+"'", password);

        try {
            final boolean ok;
            if (checkTable) {
                ok = checkTable(config);
                if (!ok) {
                    // create the schema when the test table does not exist. Do baseline migration
                    config.getDatabase().getHibernate().setHbm2ddlAuto("create");
                    config.getDatabase().setMigrationBaselineOnly(true);
                }
            } else {
                config.execSql("select 1");
                ok = true;
            }
            if (ok) {
                log.info("database configured OK, skipping initialization");
                return;
            }
        } catch (Exception e) {
            log.warn(PREFIX+"database not properly configured, attempting to initialize...");
        }

        try {
            if (!dbExists(db)) {
                execScript("createdb --encoding=UTF-8 "+db);
                if (!dbExists(db)) die(PREFIX+"error creating "+db+" database");

                // create the schema, just this time. Do baseline migration.
                config.getDatabase().getHibernate().setHbm2ddlAuto("create");
                config.getDatabase().setMigrationBaselineOnly(true);

            } else {
                log.info(db+" DB exists, not creating");
            }

            if (!dbUserExists(user)) {
                execScript("createuser --createdb --no-createrole --no-superuser --no-replication "+user);
                if (!dbUserExists(user)) die(PREFIX+"error creating '"+user+"' database user");
            } else {
                log.info("DB user '"+user+"' exists, not creating");
            }
            execScript("echo \"ALTER USER bubble WITH PASSWORD '"+ password +"'\" | psql template1");

        } catch (Exception e) {
            die(PREFIX+"Error initializing database: "+shortError(e));
        }

        // ensure connection works
        try {
            config.execSql("select 1");
            log.info("database configured OK");

        } catch (Exception e) {
            die(PREFIX+"database configuration failed, cannot run test query: "+shortError(e));
        }

        super.beforeStart(server);
    }

    public boolean checkTable(PgRestServerConfiguration config) {
        if (checkTable) {
            final String tableName = getCheckTableName(config);
            try {
                return runTableCheck(config, tableName);
            } catch (Exception e) {
                log.warn("table '"+tableName+"' not found, will create schema and do baseline migration: " + shortError(e));
                config.getDatabase().getHibernate().setHbm2ddlAuto("create");
                config.getDatabase().setMigrationBaselineOnly(true);
            }
        }
        return false;
    }

    public boolean runTableCheck(PgRestServerConfiguration config, String tableName) {
        if (!checkSafeShellArg(tableName)) invalidName("invalid table name", tableName);
        final ResultSetBean rs = config.execSql("select * from " + tableName + " limit 1");
        return rs != null && rs.rowCount() <= 1;
    }

    @Override public void onStart(RestServer server) {
        if (checkTable) {
            final PgRestServerConfiguration config = (PgRestServerConfiguration) server.getConfiguration();
            final String tableName = getCheckTableName(config);
            try {
                runTableCheck(config, tableName);
            } catch (Exception e) {
                die(PREFIX + "runTableCheck failed for table '"+tableName+"': " + shortError(e));
            }
        }
        if (isCheckRedis()) {
            try {
                server.getConfiguration().getBean(RedisService.class).keys("_");
            } catch (Exception e) {
                die(PREFIX + "error connecting to redis: " + shortError(e));
            }
        }
        super.onStart(server);
    }
}
