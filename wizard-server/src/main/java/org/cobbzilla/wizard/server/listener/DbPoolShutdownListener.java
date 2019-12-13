package org.cobbzilla.wizard.server.listener;

import com.mchange.v2.c3p0.PooledDataSource;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.server.RestServer;
import org.cobbzilla.wizard.server.RestServerLifecycleListenerBase;
import org.cobbzilla.wizard.server.config.DatabaseConfiguration;
import org.cobbzilla.wizard.server.config.HasDatabaseConfiguration;
import org.cobbzilla.wizard.server.config.RestServerConfiguration;
import org.cobbzilla.wizard.spring.config.rdbms.RdbmsConfig;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

import static org.cobbzilla.util.system.Sleep.sleep;
import static org.cobbzilla.wizard.util.SpringUtil.getBean;

@Slf4j
public class DbPoolShutdownListener<C extends RestServerConfiguration> extends RestServerLifecycleListenerBase<C> {

    @Override public void onStop(RestServer server) {
        if (server.getConfiguration() instanceof HasDatabaseConfiguration) {
            final DatabaseConfiguration db = ((HasDatabaseConfiguration) server.getConfiguration()).getDatabase();
            if (db.getPool().isEnabled()) {
                final RdbmsConfig rdbmsConfig = getBean(server.getApplicationContext(), RdbmsConfig.class);
                if (rdbmsConfig != null) {
                    final DataSource ds = rdbmsConfig.dataSource();
                    if (ds instanceof PooledDataSource) {
                        stopPool(db.getDatabaseName(), (PooledDataSource) ds);
                    }
                }
            }
        }
        super.onStop(server);
    }

    public static final long STOP_POOL_RETRY_DEFAULT = TimeUnit.SECONDS.toMillis(2);

    public long getStopPoolSleepIncrement(int i) { return STOP_POOL_RETRY_DEFAULT; }

    protected void stopPool(String dbName, PooledDataSource pool) {
        long sleep = 0;
        for (int i=0; i<5; i++) {
            if (pool != null) {
                try {
                    pool.close();
                    log.info("stopPool: stopped pooled data source: " + dbName);
                    return;
                } catch (SQLException e) {
                    log.warn("stopPool: error stopping pooled data source: " + dbName + ": " + e);
                }
            }
            sleep += getStopPoolSleepIncrement(i);
            sleep(sleep);
        }
        log.error("stopPool: giving up trying to stop pooled data source: " + dbName);
    }

}
