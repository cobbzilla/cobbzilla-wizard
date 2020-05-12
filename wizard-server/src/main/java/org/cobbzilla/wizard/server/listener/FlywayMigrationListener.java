package org.cobbzilla.wizard.server.listener;

import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.jdbc.UncheckedSqlException;
import org.cobbzilla.wizard.server.RestServer;
import org.cobbzilla.wizard.server.RestServerLifecycleListenerBase;
import org.cobbzilla.wizard.server.config.DatabaseConfiguration;
import org.cobbzilla.wizard.server.config.PgRestServerConfiguration;
import org.cobbzilla.wizard.server.config.RestServerConfiguration;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.resolver.MigrationResolver;
import org.flywaydb.core.internal.sqlscript.FlywaySqlScriptException;
import org.postgresql.util.PSQLException;

import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.util.time.TimeUtil.DATE_FORMAT_YYYYMMDD;

@Slf4j
public class FlywayMigrationListener<C extends RestServerConfiguration> extends RestServerLifecycleListenerBase<C> {

    public static final String FLYWAY_TABLE_NAME = "flyway_schema_history";
    public static final MigrationResolver[] EMPTY_MIGRATION_RESOLVERS = new MigrationResolver[0];
    protected RestServer server;

    @Override public void beforeStart(RestServer server) {
        this.server = server;
        final PgRestServerConfiguration configuration = (PgRestServerConfiguration) server.getConfiguration();
        if (configuration.getDatabase().isMigrationEnabled()) {
            migrate(configuration);
        }
        super.beforeStart(server);
    }

    protected boolean skipDefaultResolvers() { return false; }
    protected MigrationResolver[] getResolvers() { return null; }

    public String getBaselineVersion() { return DATE_FORMAT_YYYYMMDD.print(now())+"99"; }

    public void migrate(PgRestServerConfiguration configuration) {

        // check to see if flyway tables exist
        boolean baseline = false;
        try {
            configuration.execSql("SELECT * from " + FLYWAY_TABLE_NAME);
        } catch (UncheckedSqlException e) {
            if (e.getSqlException() != null && e.getSqlException() instanceof PSQLException && e.getMessage().contains(" does not exist")) {
                log.warn(FLYWAY_TABLE_NAME + " table does not exist, will baseline DB");
                baseline = true;
            } else {
                throw e;
            }
        }

        final DatabaseConfiguration dbConfig = configuration.getDatabase();
        final MigrationResolver[] resolvers = getResolvers();

        final Flyway flyway = new Flyway(new FluentConfiguration()
                .dataSource(dbConfig.getUrl(), dbConfig.getUser(), dbConfig.getPassword())
                .skipDefaultResolvers(skipDefaultResolvers())
                .resolvers(resolvers != null ? resolvers : new MigrationResolver[0])
                .baselineOnMigrate(baseline)
                .baselineVersion(MigrationVersion.fromVersion(getBaselineVersion())));

        int applied;
        try {
            applied = flyway.migrate();

        } catch (FlywaySqlScriptException e) {
            if (e.getStatement().trim().toLowerCase().startsWith("drop ") && e.getMessage().contains("does not exist")) {
                log.info("migrate: drop statement ("+e.getStatement()+") failed, ignoring: "+e, e);
                return;
            } else {
                throw e;
            }

        } catch (FlywayException e) {

            if (e.getMessage().contains("Migration checksum mismatch")) {
                log.warn("migrate: checksum mismatch; attempting to repair");
                flyway.repair();

                log.info("migrate: successfully repaired, re-trying migrate...");
                applied = flyway.migrate();

            } else {
                throw e;
            }
        }
        log.info("migrate: successfully applied "+applied+" migrations");
    }

}
