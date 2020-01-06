package org.cobbzilla.wizard.server.listener;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.cache.redis.RedisService;
import org.cobbzilla.wizard.server.RestServer;
import org.cobbzilla.wizard.server.RestServerLifecycleListenerBase;
import org.cobbzilla.wizard.server.config.PgRestServerConfiguration;

import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.string.StringUtil.checkSafeShellArg;
import static org.cobbzilla.util.system.CommandShell.execScript;
import static org.cobbzilla.wizard.server.config.PgRestServerConfiguration.dbExists;
import static org.cobbzilla.wizard.server.config.PgRestServerConfiguration.dbUserExists;

@Slf4j @Accessors(chain=true)
public class SystemInitializerListener extends RestServerLifecycleListenerBase {

    public static final String PREFIX = SystemInitializerListener.class.getSimpleName() + ": ";
    public static final String SAFE_CHARS_MESSAGE = " -- this can only contain letters, numbers, spaces, tabs, and these special characters: -._/=";

    @Getter @Setter private boolean checkRedis = true;

    @Override public void beforeStart(RestServer server) {

        final PgRestServerConfiguration config = (PgRestServerConfiguration) server.getConfiguration();
        final String db = config.getDatabase().getDatabaseName();
        final String user = config.getDatabase().getUser();
        final String password = config.getDatabase().getPassword();

        // we're going to use these in shell scripts, so ensure they are safe
        if (!checkSafeShellArg(db)) die(PREFIX+"invalid db name: "+db+SAFE_CHARS_MESSAGE);
        if (!checkSafeShellArg(user)) die(PREFIX+"invalid db user name: "+user+SAFE_CHARS_MESSAGE);
        if (!checkSafeShellArg(password)) die(PREFIX+": invalid password for user '"+user+"': "+password+SAFE_CHARS_MESSAGE);

        try {
            config.execSql("select 1");
            log.info("database configured OK, skipping initialization");
            return;

        } catch (Exception e) {
            log.warn(PREFIX+"database not configured, attempting to initialize...");
        }

        // does a database exist?
        try {
            if (!dbExists(db)) {
                execScript("createdb --encoding=UTF-8 "+db);
                if (!dbExists(db)) die(PREFIX+"error creating "+db+" database");

            } else {
                log.info(db+" DB exists, not creating");
            }

            if (!dbUserExists(user)) {
                execScript("createuser --createdb --no-createrole --no-superuser --no-replication "+user);
                if (!dbUserExists(user)) die(PREFIX+"error creating '"+user+"' database user");

                execScript("echo \"ALTER USER bubble WITH PASSWORD '"+ password +"'\" | psql");
            } else {
                log.info("DB user '"+user+"' exists, not creating");
            }
        } catch (Exception e) {
            die(PREFIX+"Error initializing database: "+shortError(e));
        }

        // ensure connection works
        try {
            config.execSql("select 1");
            log.info("database configured OK");
            return;

        } catch (Exception e) {
            die(PREFIX+"database configuration failed, cannot run test query: "+shortError(e));
        }

        super.beforeStart(server);
    }

    @Override public void onStart(RestServer server) {
        if (isCheckRedis()) {
            try {
                final RedisService redis = server.getConfiguration().getBean(RedisService.class);
                redis.keys("_");
            } catch (Exception e) {
                die(PREFIX + "error connecting to redis: " + shortError(e));
            }
        }
        super.onStart(server);
    }
}
