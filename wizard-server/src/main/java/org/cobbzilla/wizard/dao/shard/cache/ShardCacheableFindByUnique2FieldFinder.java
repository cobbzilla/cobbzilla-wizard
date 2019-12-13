package org.cobbzilla.wizard.dao.shard.cache;

import org.cobbzilla.wizard.dao.shard.AbstractShardedDAO;
import org.cobbzilla.wizard.dao.shard.SingleShardDAO;
import org.cobbzilla.wizard.dao.shard.task.ShardFindFirstBy2FieldsTask;
import org.cobbzilla.wizard.dao.shard.task.ShardTaskFactory;
import org.cobbzilla.wizard.model.shard.Shardable;

public class ShardCacheableFindByUnique2FieldFinder<E extends Shardable, D extends SingleShardDAO<E>> extends ShardCacheableFinder<E, D> {

    public ShardCacheableFindByUnique2FieldFinder(AbstractShardedDAO<E, D> shardedDAO, long timeout) { super(shardedDAO, timeout); }

    public ShardCacheableFindByUnique2FieldFinder(AbstractShardedDAO<E, D> shardedDAO, long timeout, boolean useCache) {
        super(shardedDAO, timeout, useCache);
    }

    @Override public E find(Object... args) {
        final String f1 = args[0].toString();
        final Object v1 = args[1];
        final String f2 = args[2].toString();
        final Object v2 = args[3];

        D dao = null;
        if (shardedDAO.getHashOn().equals(f1)) {
            dao = shardedDAO.getDAO((String) v1);
        } else if (shardedDAO.getHashOn().equals(f2)) {
            dao = shardedDAO.getDAO((String) v2);
        }
        if (dao != null) return dao.findByUniqueFields(f1, v1, f2, v2);

        // have to search all shards for it
        return shardedDAO.queryShardsUnique((ShardTaskFactory<E, D, E>) new ShardFindFirstBy2FieldsTask.Factory(f1, v1, f2, v2), "findByUniqueFields");
    }
}
