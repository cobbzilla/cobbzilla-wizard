package org.cobbzilla.wizard.server.listener;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.jdbc.ResultSetBean;
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

    @Getter(lazy=true) private static final String flywayTableName = Flyway.configure().getTable();

    protected RestServer server;

    @Override public void beforeStart(RestServer server) {
        this.server = server;
        final PgRestServerConfiguration configuration = (PgRestServerConfiguration) server.getConfiguration();
        if (configuration.getDatabase().migrationEnabled()) {
            migrate(configuration);
        }
        super.beforeStart(server);
    }

    protected boolean skipDefaultResolvers() { return false; }
    protected MigrationResolver[] getResolvers() { return null; }

    public String getBaselineVersion() { return DATE_FORMAT_YYYYMMDD.print(now()) + "99"; }

    public void migrate(@NonNull final PgRestServerConfiguration configuration) {

        // check to see if flyway tables exist
        final var flywayTable = getFlywayTableName();
        final boolean baselineOnly = configuration.getDatabase().isMigrationBaselineOnly();
        final var baseline = baselineOnly || checkIfBaseline(configuration, flywayTable);
        final var baselineVersion = baseline ? MigrationVersion.fromVersion(getBaselineVersion())
                                             : MigrationVersion.EMPTY;
        if (baseline) {
            log.warn(flywayTable + " table does not exist or is empty, will baseline DB with " + baselineVersion);
        }

        final DatabaseConfiguration dbConfig = configuration.getDatabase();
        final MigrationResolver[] resolvers = getResolvers();

        final Flyway flyway = new Flyway(new FluentConfiguration()
                .dataSource(dbConfig.getUrl(), dbConfig.getUser(), dbConfig.getPassword())
                .skipDefaultResolvers(baselineOnly || skipDefaultResolvers())
                .resolvers(resolvers != null && !baselineOnly ? resolvers : new MigrationResolver[0])
                .baselineOnMigrate(baseline)
                .baselineVersion(baselineVersion));

        int applied;
        try {
            applied = flyway.migrate();
        } catch (FlywaySqlScriptException e) {
            if (e.getStatement().trim().toLowerCase().startsWith("drop ") && e.getMessage().contains("does not exist")) {
                log.info("migrate: drop statement ("+e.getStatement()+") failed, ignoring: "+e, e);
                return;
            } else {
                // consider checking for errors like:
                // "Detected resolved migration not applied to database: 2020071901"
                // and trying to start in spite of them
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

    private boolean checkIfBaseline(@NonNull final PgRestServerConfiguration configuration,
                                    @NonNull final String flywayTable) {
        final ResultSetBean rs;
        try {
            rs = configuration.execSql("SELECT 1 FROM " + flywayTable + " LIMIT 1");
        } catch (UncheckedSqlException e) {
            if (e.getSqlException() == null || !(e.getSqlException() instanceof PSQLException)
                    || !e.getMessage().contains(" does not exist")) {
                throw e;
            }
            // else, if table is not present, set baseline:
            return true;
        }

        // Also, if present flyway migration history table is empty -> set baseline
        if (rs.rowCount() == 0) {
            // dropping the existing empty table so flyway inner baseline will work properly
            configuration.execSql("DROP TABLE " + flywayTable);
            return true;
        }

        return false;
    }

}
