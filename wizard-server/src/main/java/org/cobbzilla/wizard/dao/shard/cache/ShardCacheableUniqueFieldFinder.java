package org.cobbzilla.wizard.dao.shard.cache;

import org.cobbzilla.wizard.dao.shard.AbstractShardedDAO;
import org.cobbzilla.wizard.dao.shard.SingleShardDAO;
import org.cobbzilla.wizard.dao.shard.task.ShardFindFirstByFieldTask;
import org.cobbzilla.wizard.dao.shard.task.ShardTaskFactory;
import org.cobbzilla.wizard.model.shard.Shardable;

public class ShardCacheableUniqueFieldFinder<E extends Shardable, D extends SingleShardDAO<E>> extends ShardCacheableFinder<E, D> {

    public ShardCacheableUniqueFieldFinder(AbstractShardedDAO<E, D> shardedDAO, long timeout, boolean useCache) {
        super(shardedDAO, timeout, useCache);
    }

    @Override public E find(Object... args) {
        final String field = args[0].toString();
        final String value = (String) args[1];

        if (shardedDAO.getHashOn().equals(field)) return shardedDAO.getDAO(value).get(value);

        // have to search all shards for it
        return shardedDAO.queryShardsUnique((ShardTaskFactory<E, D, E>) new ShardFindFirstByFieldTask.Factory(field, value), "findByUniqueField");
    }
}
