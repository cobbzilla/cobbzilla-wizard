package org.cobbzilla.wizard.dao.shard;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.cobbzilla.util.system.Sleep;

import java.util.concurrent.TimeUnit;

import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.util.time.TimeUtil.formatDurationFrom;

@AllArgsConstructor @Slf4j
class DAOInitializer extends Thread {

    public static final int MAX_INIT_DAO_ATTEMPTS = 5;

    private AbstractShardedDAO shardedDAO;

    @Override public void run() {
        int attempt = 1;
        final String prefix = "initAllDAOs(" + shardedDAO.getEntityClass().getSimpleName() + "): ";
        String shardSetName = null;
        long start = now();
        log.info(prefix+"starting");
        while (attempt <= MAX_INIT_DAO_ATTEMPTS) {
            Sleep.sleep(10000 + RandomUtils.nextLong(1000, 5000));
            try {
                boolean ok = false;
                while (!ok) {
                    try {
                        if (shardSetName == null) shardSetName = shardedDAO.getMasterDbConfiguration().getShardSetName(shardedDAO.getEntityClass());
                        ok = !shardSetName.isEmpty();
                    } catch (Exception ignored) {}
                    Sleep.sleep(200);
                }
                final ShardMapDAO shardDAO = shardedDAO.getShardDAO();
                if (shardDAO != null) {
                    shardedDAO.toDAOs(shardDAO.findByShardSet(shardSetName));
                    log.info(prefix+"completed in "+formatDurationFrom(start));
                    return;
                } else {
                    log.warn(prefix+"shardDAO was null: "+shardSetName);
                }

            } catch (Exception e) {
                log.warn(prefix + "attempt " + attempt + ": error initializing: " + e, e);
            } finally {
                attempt++;
            }
            Sleep.sleep(TimeUnit.SECONDS.toMillis(4 * attempt));
        }
        if (attempt >= MAX_INIT_DAO_ATTEMPTS) log.error(prefix + ": too many errors, giving up");
    }
}
