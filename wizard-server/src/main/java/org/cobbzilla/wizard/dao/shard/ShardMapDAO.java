package org.cobbzilla.wizard.dao.shard;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.cobbzilla.util.collection.FieldTransformer;
import org.cobbzilla.util.collection.mappy.MappyList;
import org.cobbzilla.wizard.dao.AbstractCRUDDAO;
import org.cobbzilla.wizard.model.shard.ShardIO;
import org.cobbzilla.wizard.model.shard.ShardMap;
import org.cobbzilla.wizard.model.shard.ShardSetStatus;

import javax.validation.Valid;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;

@Slf4j
public abstract class ShardMapDAO<E extends ShardMap> extends AbstractCRUDDAO<E> {

    private static final long CACHE_TIMEOUT = TimeUnit.MINUTES.toMillis(60);

    private final AtomicReference<List<E>> flatCache = new AtomicReference<>();
    private final AtomicReference<MappyList<String, E>> readCache = new AtomicReference<>();
    private final AtomicReference<MappyList<String, E>> writeCache = new AtomicReference<>();
    private final AtomicLong lastRefresh = new AtomicLong(0);
    private final AtomicReference<Thread> refresher = new AtomicReference<>();

    @Override public List<E> findAll() { return refreshCache(); }

    public List<E> refreshCache() { return refreshCache(false); }

    public List<E> refreshCache(boolean force) {
        final List<E> all;
        if ((force || now() - lastRefresh.get() > CACHE_TIMEOUT) && refresher.get() == null) {
            synchronized (refresher) {
                if ((force || now() - lastRefresh.get() > CACHE_TIMEOUT) && refresher.get() == null) {
                    try {
                        refresher.set(Thread.currentThread());
                        final MappyList<String, E> newReadCache = new MappyList<>();
                        final MappyList<String, E> newWriteCache = new MappyList<>();
                        final List<E> newFlatCache = new ArrayList<>();
                        all = super.findAll();
                        for (E shardMap : all) {

                            final List<E> readShards = newReadCache.getAll(shardMap.getShardSet());
                            if (shardMap.isAllowRead()) readShards.add(shardMap);

                            final List<E> writeShards = newWriteCache.getAll(shardMap.getShardSet());
                            if (shardMap.isAllowWrite()) writeShards.add(shardMap);

                            newFlatCache.add(shardMap);
                        }

                        // validate
                        for (String shardSet : toNames(newFlatCache)) {
                            if (!validate(shardSet, newReadCache.getAll(shardSet))) log.warn("Invalid read-shard set for " + shardSet);
                            if (!validate(shardSet, newWriteCache.getAll(shardSet))) log.warn("Invalid write-shard set for " + shardSet);
                        }

                        readCache.set(newReadCache);
                        writeCache.set(newWriteCache);
                        flatCache.set(newFlatCache);

                        lastRefresh.set(now());

                    } finally {
                        refresher.set(null);
                    }
                } else {
                    all = flatCache.get();
                }
            }
        } else {
            all = flatCache.get();
        }
        return all;
    }

    @Override public E postCreate(E entity, Object context) {
        refreshCache(true);
        return super.postCreate(entity, context);
    }

    @Override public E postUpdate(@Valid E entity, Object context) {
        refreshCache(true);
        return super.postUpdate(entity, context);
    }

    @Override public void delete(String uuid) {
        super.delete(uuid);
        refreshCache(true);
    }

    public static final FieldTransformer TO_SHARD_SET = new FieldTransformer("shardSet");
    private Set<String> toNames(List<E> flatCache) {
        return new HashSet<>(CollectionUtils.collect(flatCache, TO_SHARD_SET));
    }

    public List<E> getShardList(String shardSet, int logicalShard, ShardIO shardIO) {
        List<E> maps = getShardList(shardSet, shardIO);
        final List<E> matches = new ArrayList<>();
        for (E m : maps) {
            if (m.mapsShard(logicalShard)) matches.add(m);
        }
        return matches;
    }

    protected List<E> getShardList(String shardSet, ShardIO shardIO) {
        refreshCache();
        switch (shardIO) {
            case read: return readCache.get().getAll(shardSet);
            case write: return writeCache.get().getAll(shardSet);
            default: return die("getShardList: invalid shardIO: "+shardIO);
        }
    }

    protected List<E> getShardList(String shardSet, int logicalShard) {
        refreshCache();
        final Set<E> maps = new HashSet<>();
        for (E map : readCache.get().getAll(shardSet)) if (map.mapsShard(logicalShard)) maps.add(map);
        for (E map : writeCache.get().getAll(shardSet)) if (map.mapsShard(logicalShard)) maps.add(map);
        return new ArrayList<>(maps);
    }

    public List<E> findReadShards(String shardSet) {
        refreshCache();
        return readCache.get().getAll(shardSet);
    }

    public List<E> findWriteShards(String shardSet) {
        refreshCache();
        return writeCache.get().getAll(shardSet);
    }

    public List<E> findAllShards(String shardSet) {
        refreshCache();
        final Set<E> shards = new HashSet<>();
        shards.addAll(readCache.get().getAll(shardSet));
        shards.addAll(writeCache.get().getAll(shardSet));
        return new ArrayList<>(shards);
    }

    private List<E> findReadShards(E[] shards) {
        final List<E> list = new ArrayList<>();
        for (E shard : shards) if (shard.isAllowRead()) list.add(shard);
        return list;
    }

    private List<E> findWriteShards(E[] shards) {
        final List<E> list = new ArrayList<>();
        for (E shard : shards) if (shard.isAllowWrite()) list.add(shard);
        return list;
    }

    public List<E> findShards(String shardSet, ShardIO shardIO) {
        switch (shardIO) {
            case read:  return findReadShards(shardSet);
            case write: return findWriteShards(shardSet);
            default: return die("findShards: invalid ShardIO: "+shardIO);
        }
    }

    public List<E> findByShardSet(String shardSet) { return findByField("shardSet", shardSet); }

    public ShardSetStatus validate(String shardSet) {
        final List<E> readShards = findReadShards(shardSet);
        final List<E> writeShards = findWriteShards(shardSet);
        return new ShardSetStatus(shardSet,
                readShards,  validate(shardSet, readShards),
                writeShards, validate(shardSet, writeShards));
    }

    public ShardSetStatus validate(String shardSet, E[] shards) {
        final List<E> readShards = findReadShards(shards);
        final List<E> writeShards = findWriteShards(shards);
        return new ShardSetStatus(shardSet,
                readShards,  validate(shardSet, readShards),
                writeShards, validate(shardSet, writeShards));
    }

    public ShardSetStatus validateWithShardRemoved(String shardSet, E shard) {
        final List<E> readShards = new ArrayList<>(findReadShards(shardSet));
        final List<E> writeShards = new ArrayList<>(findWriteShards(shardSet));
        readShards.remove(shard);
        writeShards.remove(shard);
        return new ShardSetStatus(shardSet,
                readShards,  validate(shardSet, readShards),
                writeShards, validate(shardSet, writeShards));
    }

    public boolean validate(String shardSet, List<E> list) {

        if (list.isEmpty()) {
            log.warn("validate("+shardSet+"): no shards defined");
            return false;
        }

        // ensure we have full coverage of all logical shards
        final int logicalShardCount = getLogicalShardCount(shardSet);
        final TreeSet<E> sorted = new TreeSet<>(ShardMap.RANGE_COMPARATOR);
        sorted.addAll(list);
        int expecting = 0;
        boolean foundEnd = false;
        for (ShardMap map : sorted) {
            if (map.getRange().getLogicalStart() > expecting) {
                log.warn("validate("+ shardSet +"): expected shard logical range to start at "+expecting+", but was "+map.getRange().getLogicalStart());
                return false;
            } else if (map.getRange().getLogicalEnd() > expecting) {
                expecting = map.getRange().getLogicalEnd();
            }
            if (map.getRange().getLogicalEnd() == logicalShardCount) {
                foundEnd = true;
                break;
            }
        }
        if (!foundEnd) {
            log.warn("validate("+ shardSet +"): shards do not cover entire set of logical shards");
            return false;
        }
        return true;
    }

    protected abstract int getLogicalShardCount(String shardSet);
}
