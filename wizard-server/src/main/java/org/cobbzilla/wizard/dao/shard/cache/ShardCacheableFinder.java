package org.cobbzilla.wizard.dao.shard.cache;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.json.JsonUtil;
import org.cobbzilla.wizard.dao.shard.AbstractShardedDAO;
import org.cobbzilla.wizard.dao.shard.SingleShardDAO;
import org.cobbzilla.wizard.model.shard.Shardable;

import java.util.concurrent.TimeUnit;

import static org.cobbzilla.util.json.JsonUtil.toJsonOrDie;
import static org.cobbzilla.wizard.dao.shard.AbstractShardedDAO.NULL_CACHE;

@AllArgsConstructor @Accessors(chain=true)
public abstract class ShardCacheableFinder<E extends Shardable, D extends SingleShardDAO<E>> implements CacheableFinder {

    protected AbstractShardedDAO<E, D> shardedDAO;
    @Getter protected long cacheTimeoutSeconds = TimeUnit.MINUTES.toSeconds(20);
    @Getter @Setter protected boolean useCache = true;

    public ShardCacheableFinder(AbstractShardedDAO<E, D> dao, long timeout) { this(dao, timeout, true); }

    public E get(String cacheKey, Object... args) {
        if (!useCache) return (E) find(args);
        final String shardSetName = shardedDAO.getShardConfiguration().getName();
        cacheKey = shardSetName +":" + cacheKey;
        E entity = null;
        final String json = shardedDAO.getShardCache().get(cacheKey);
        if (json == null) {
            entity = (E) find(args);
            if (entity == null) {
                shardedDAO.getShardCache().set(cacheKey, NULL_CACHE, "EX", getCacheTimeoutSeconds());
                final String cacheRefsKey = shardedDAO.getCacheRefsKey(NULL_CACHE);
                shardedDAO.getShardCache().lpush(cacheRefsKey, cacheKey);
            } else {
                shardedDAO.getShardCache().set(cacheKey, toJsonOrDie(entity), "EX", getCacheTimeoutSeconds());
                shardedDAO.getShardCache().lpush(shardedDAO.getCacheRefsKey(entity.getUuid()), cacheKey);
            }
        } else if (!json.equals(NULL_CACHE)) {
            entity = JsonUtil.fromJsonOrDie(json, shardedDAO.getEntityClass());
            shardedDAO.getShardCache().set(cacheKey, json, "EX", getCacheTimeoutSeconds());
        }
        return entity;
    }

}
