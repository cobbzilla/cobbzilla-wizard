package org.cobbzilla.wizard.dao.shard;

import lombok.AllArgsConstructor;
import org.cobbzilla.wizard.model.shard.ShardMap;
import org.cobbzilla.wizard.model.shard.Shardable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@AllArgsConstructor
public class DaoMapCleaner<E extends Shardable, D extends SingleShardDAO<E>> implements Runnable {

    private Map<ShardMap, D> daos;
    private ShardMapDAO shardMapDAO;

    private final AtomicReference<Thread> thread = new AtomicReference<>();

    public void start () {
        synchronized (thread) {
            thread.set(new Thread(this));
            thread.get().setDaemon(true);
            thread.get().start();
        }
    }

    public void run() {
        final List<ShardMap> all = shardMapDAO.refreshCache();
        final List<ShardMap> toRemove = new ArrayList<>();
        for (Map.Entry<ShardMap, D> entry : daos.entrySet()) {
            final ShardMap shardMap = entry.getKey();
            if (!shardMap.isDefaultShard() && !all.contains(shardMap)) {
                toRemove.add(shardMap);
                entry.getValue().cleanup();
            }
        }
        for (ShardMap map : toRemove) daos.remove(map);
    }
}
