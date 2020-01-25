package org.cobbzilla.wizard.dao.shard;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.SingletonList;
import org.cobbzilla.util.collection.mappy.MappyList;
import org.cobbzilla.util.reflect.ReflectionUtil;
import org.cobbzilla.wizard.cache.redis.HasRedisConfiguration;
import org.cobbzilla.wizard.cache.redis.RedisService;
import org.cobbzilla.wizard.dao.DAO;
import org.cobbzilla.wizard.dao.SearchResults;
import org.cobbzilla.wizard.dao.shard.cache.ShardCacheableFindByUnique2FieldFinder;
import org.cobbzilla.wizard.dao.shard.cache.ShardCacheableFindByUnique3FieldFinder;
import org.cobbzilla.wizard.dao.shard.cache.ShardCacheableIdentityFinder;
import org.cobbzilla.wizard.dao.shard.cache.ShardCacheableUniqueFieldFinder;
import org.cobbzilla.wizard.dao.shard.task.*;
import org.cobbzilla.wizard.model.search.SearchQuery;
import org.cobbzilla.wizard.model.shard.ShardIO;
import org.cobbzilla.wizard.model.shard.ShardMap;
import org.cobbzilla.wizard.model.shard.ShardRange;
import org.cobbzilla.wizard.model.shard.Shardable;
import org.cobbzilla.wizard.server.ApplicationContextConfig;
import org.cobbzilla.wizard.server.CustomBeanResolver;
import org.cobbzilla.wizard.server.RestServer;
import org.cobbzilla.wizard.server.config.DatabaseConfiguration;
import org.cobbzilla.wizard.server.config.HasDatabaseConfiguration;
import org.cobbzilla.wizard.server.config.RestServerConfiguration;
import org.cobbzilla.wizard.server.config.ShardSetConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.annotation.Transactional;

import javax.validation.Valid;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.cobbzilla.util.daemon.Await.awaitAndCollect;
import static org.cobbzilla.util.daemon.Await.awaitFirst;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.reflect.ReflectionUtil.*;
import static org.cobbzilla.util.security.ShaUtil.sha256_hex;
import static org.cobbzilla.wizard.model.Identifiable.UUID;
import static org.cobbzilla.wizard.resources.ResourceUtil.timeoutEx;
import static org.cobbzilla.wizard.util.SpringUtil.autowire;

@Transactional @Slf4j
public abstract class AbstractShardedDAO<E extends Shardable, D extends SingleShardDAO<E>> implements DAO<E> {

    public static final int MAX_QUERY_RESULTS = 200;

    public static final String NULL_CACHE = "__null__";

    @Autowired private HasDatabaseConfiguration dbConfig;
    @Autowired private HasRedisConfiguration redisConfig;
    @Autowired private RestServer server;
    @Autowired private RedisService redisService;

    private long getCacheTimeoutSeconds() { return TimeUnit.MINUTES.toSeconds(30); }

    @Getter(lazy=true) private final RedisService shardCache = initShardCache();
    private RedisService initShardCache() { return redisService.prefixNamespace("shard-cache-"+getEntityClass().getName()); }

    @Getter private final Class<E> entityClass;
    @Getter private final Class<D> singleShardDaoClass;
    @Getter private final String hashOn;

    public E newEntity() { return instantiate(getEntityClass()); }

    private final Map<ShardMap, D> daos = new ConcurrentHashMap<>();

    private static final long DAO_MAP_CLEAN_INTERVAL = TimeUnit.DAYS.toMillis(1);
    private final AtomicLong daosLastCleaned = new AtomicLong(0);
    private void cleanDaoMap() { new DaoMapCleaner<>(daos, getShardDAO()).start(); }

    public static final long DEFAULT_SHARD_QUERY_TIMEOUT = TimeUnit.SECONDS.toMillis(30);
    public long getShardQueryTimeout (String method) { return DEFAULT_SHARD_QUERY_TIMEOUT; }
    public long getShardSearchTimeout () { return getShardQueryTimeout("search"); }

    public static final int DEFAULT_MAX_QUERY_THREADS = 100;
    protected int getMaxQueryThreads () { return DEFAULT_MAX_QUERY_THREADS; }

    private final BlockingQueue<Runnable> queryWorkerQueue = new LinkedBlockingQueue<>();
    private final ThreadPoolExecutor queryWorkerPool = new ThreadPoolExecutor(getMaxQueryThreads()/2, getMaxQueryThreads(), 10, TimeUnit.MINUTES, queryWorkerQueue);

    protected ApplicationContext getApplicationContext(DatabaseConfiguration database) {

        final HasDatabaseConfiguration singleShardConfig = instantiate(dbConfig.getClass());
        copy(singleShardConfig, dbConfig);
        singleShardConfig.setDatabase(database);
        singleShardConfig.getDatabase().getHibernate().setHbm2ddlAuto("validate");

        final ApplicationContextConfig ctxConfig = new ApplicationContextConfig()
                .setConfig((RestServerConfiguration) singleShardConfig)
                .setResolvers(new CustomBeanResolver[] {new CustomShardedDAOResolver(server.getApplicationContext())})
                .setSpringContextPath(getSpringShardContextPath());
        final ApplicationContext applicationContext = server.buildSpringApplicationContext(ctxConfig);
        ((RestServerConfiguration) singleShardConfig).setApplicationContext(applicationContext);

        return applicationContext;
    }

    protected String getSpringShardContextPath() { return "spring-shard.xml"; }

    public abstract ShardSetConfiguration getShardConfiguration();
    protected abstract DatabaseConfiguration getMasterDbConfiguration();
    protected abstract ShardMapDAO getShardDAO();

    public AbstractShardedDAO() {
        this.entityClass = getFirstTypeParam(getClass(), Shardable.class);
        this.hashOn = instantiate(this.entityClass).getHashToShardField();
        this.singleShardDaoClass = initShardDaoClass();
    }
    protected Class<D> initShardDaoClass() { return getFirstTypeParam(getClass(), SingleShardDAO.class); }

    @Getter(lazy=true) private final ShardMap defaultShardMap = initDefaultShardMap();
    private ShardMap initDefaultShardMap () {
        log.warn("no shards defined, using master DB only: "+getShardConfiguration().getName());
        return new ShardMap()
                .setShardSet(getShardConfiguration().getName())
                .setRange(new ShardRange(0, Integer.MAX_VALUE))
                .setUrl(getMasterDbConfiguration().getUrl())
                .setAllowRead(true)
                .setAllowWrite(true)
                .setDefaultShard(true);
    }

    private static final Set<String> daosInitialized = new HashSet<>();
    public void initAllDAOs () {
        if (!daosInitialized.contains(getClass().getName())) {
            synchronized (daosInitialized) {
                if (!daosInitialized.contains(getClass().getName())) {
                    daosInitialized.add(getClass().getName());
                    new DAOInitializer(this).start();
                }
            }
        }
    }

    protected List<D> getAllDAOs(Serializable id) {
        final List shards = getShardDAO().getShardList(getShardConfiguration().getName(), getLogicalShard(id));
        if (shards.isEmpty()) shards.add(getDefaultShardMap());
        return toDAOs(shards);
    }

    protected List<D> getAllDAOs() {
        final List shards = getShardDAO().findAllShards(getShardConfiguration().getName());
        if (shards.isEmpty()) shards.add(getDefaultShardMap());
        return toDAOs(shards);
    }

    protected List<D> getAllDAOs(E entity) { return getAllDAOs((Serializable) getIdToHash(entity)); }

    protected List<D> getDAOs(Serializable id, ShardIO shardIO) {
        List<ShardMap> shardMaps = getShardDAO().getShardList(getShardConfiguration().getName(), getLogicalShard(id), shardIO);
        if (shardMaps.isEmpty()) shardMaps = new SingletonList<>(getDefaultShardMap());
        final List<D> found = toDAOs(shardMaps);
        return found;
    }

    protected List<D> getDAOs(ShardIO shardIO) {
        List<ShardMap> shards;
        switch (shardIO) {
            case read: shards = getReadShards(); break;
            case write: shards = getWriteShards(); break;
            default: return die("getDAOs: invalid shardIO: "+shardIO);
        }
        if (shards.isEmpty()) shards = new SingletonList<>(getDefaultShardMap());
        return toDAOs(shards);
    }

    protected List<D> getDAOs(E entity, ShardIO shardIO) {
        final Object value = getIdToHash(entity);
        if (value == null) die("getDAOs: value of hashOn field ("+hashOn+") was null");
        return getDAOs(value.toString(), shardIO);
    }

    protected int getLogicalShard(Serializable id) {
        final String hash = sha256_hex(id.toString()).substring(0, 7);
        Long val;
        try {
            val = Long.valueOf(hash, 16);
        } catch (NumberFormatException e) {
            log.warn("getLogicalShard: invalid hex value: "+hash+" (returning 0): "+e);
            return 0;
        }
        return (int) (Math.abs(val) % getShardConfiguration().getLogicalShards());
    }

    protected List<D> toDAOs(Collection<ShardMap> shardMaps) {
        final List<D> list = new ArrayList<>();
        for (ShardMap map : shardMaps) list.add(toDAO(map));
        return list;
    }

    private D toDAO(ShardMap shardMap) {
        if (now() - daosLastCleaned.get() > DAO_MAP_CLEAN_INTERVAL) {
            cleanDaoMap();
            daosLastCleaned.set(now());
        }
        D dao = daos.get(shardMap);
        if (dao == null) {
            synchronized (daos) {
                dao = buildDAO(shardMap, singleShardDaoClass);
                daos.put(shardMap, dao);
            }
        }
        return dao;
    }

    private static final Map<String, Map<ShardMap, SingleShardDAO>> globalCache = new ConcurrentHashMap<>();
    private D buildDAO(ShardMap map, Class<D> singleShardDaoClass) {

        final String shardClass = singleShardDaoClass.getName();
        Map<ShardMap, SingleShardDAO> shardCache = globalCache.get(shardClass);
        if (shardCache == null) {
            synchronized (globalCache) {
                shardCache = globalCache.get(shardClass);
                if (shardCache == null) {
                    shardCache = new ConcurrentHashMap<>();
                    globalCache.put(shardClass, shardCache);
                }
            }
        }

        SingleShardDAO dao = shardCache.get(map);
        if (dao == null) {
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (shardCache) {
                dao = shardCache.get(map);
                if (dao == null) {
                    // Wire-up the DAO's database and hibernateTemplate to point to the shard DB
                    final DatabaseConfiguration database = getMasterDbConfiguration().getShardDatabaseConfiguration(map);
                    final ApplicationContext ctx = getApplicationContext(database);
                    dao = autowire(ctx, instantiate(singleShardDaoClass));
                    dao.initialize(map);
                    shardCache.put(map, dao);
                    log.debug("buildDAO(" + map + "): using new value for " + getEntityClass().getSimpleName());
                } else {
                    log.debug("buildDAO(" + map + "): using cached value for " + getEntityClass().getSimpleName());
                }
            }
        }
        return (D) dao;
    }

    public D getDAO(Serializable id) { return getDAO(id, ShardIO.read); }
    protected D getDAO(Serializable id, ShardIO shardIO) { return pickRandom(getDAOs(id, shardIO)); }

    protected D getDAO(E entity) { return getDAO(entity, ShardIO.read); }
    protected D getDAO(E entity, ShardIO shardIO) {
        final Object value = getIdToHash(entity);
        if (value == null) die("hashOn field "+hashOn+" was null");
        return getDAO(value.toString(), shardIO);
    }

    protected Object getIdToHash(E entity) { return ReflectionUtil.get(entity, hashOn); }

    @Transactional(readOnly=true)
    public List<D> getNonOverlappingDAOs() {
        List<ShardMap> shards = getReadShards();
        if (shards.isEmpty()) {
            shards = new SingletonList<>(getDefaultShardMap());
        } else {
            final MappyList<ShardRange, ShardMap> nonOverlapping = new MappyList<>();
            for (ShardMap shard : shards) {
                nonOverlapping.put(shard.getRange(), shard);
            }
            shards = new ArrayList<>();
            for (ShardRange range : nonOverlapping.keySet()) {
                shards.add(pickRandom(nonOverlapping.getAll(range)));
            }
        }
        return toDAOs(shards);
    }

    public List<ShardMap> getReadShards()  { return getShardDAO().findReadShards(getShardConfiguration().getName()); }
    public List<ShardMap> getWriteShards() { return getShardDAO().findWriteShards(getShardConfiguration().getName()); }
    public List<ShardMap> getAllShards()   { return getShardDAO().findAllShards(getShardConfiguration().getName()); }

    @Transactional(readOnly=true) // todo
    @Override public SearchResults<E> search(SearchQuery searchQuery) { return notSupported(); }

    @Transactional(readOnly=true) // todo
    @Override public SearchResults<E> search(SearchQuery searchQuery, String entityType) { return notSupported(); }

    @Transactional(readOnly=true)
    @Override public E get(Serializable id) {
        return new ShardCacheableIdentityFinder<>(this, getCacheTimeoutSeconds()).get(id.toString(), id);
    }

    @Transactional(readOnly=true)
    @Override public List<E> findAll() {
        final List<E> results = new ArrayList<>();
        for (D dao : getNonOverlappingDAOs()) {
            results.addAll(dao.findAll());
        }
        return results;
    }

    @Transactional(readOnly=true)
    @Override public E findByUuid(final String uuid) { return findByUniqueField(UUID, uuid); }

    @Transactional(readOnly=true)
    public E findByUuid(final String uuid, boolean useCache) { return findByUniqueField(UUID, uuid, useCache); }

    @Transactional(readOnly=true)
    @Override public E findByUniqueField(String field, Object value) { return findByUniqueField(field, value, true); }

    @Transactional(readOnly=true)
    public E findByUniqueField(String field, Object value, boolean useCache) {
        return new ShardCacheableUniqueFieldFinder<>(this, getCacheTimeoutSeconds(), useCache).get("unique-field:"+field+":"+value, field, value);
    }

    @Transactional(readOnly=true)
    public E findByUniqueFields(String f1, Object v1, String f2, Object v2) {
        return new ShardCacheableFindByUnique2FieldFinder<>(this, getCacheTimeoutSeconds()).get("unique-fields2:"+f1+":"+v1+":"+f2+":"+v2, f1, v1, f2, v2);
    }

    @Transactional(readOnly=true)
    public E findByUniqueFieldsNoCache(String f1, Object v1, String f2, Object v2) {
        return new ShardCacheableFindByUnique2FieldFinder<>(this, getCacheTimeoutSeconds(), false).get("unique-fields2:"+f1+":"+v1+":"+f2+":"+v2, f1, v1, f2, v2);
    }

    @Transactional(readOnly=true)
    public E findByUniqueFields(String f1, Object v1, String f2, Object v2, String f3, Object v3) {
        return new ShardCacheableFindByUnique3FieldFinder<>(this, getCacheTimeoutSeconds()).get("unique-fields3:"+f1+":"+v1+":"+f2+":"+v2, f1, v1, f2, v2, f3, v3);
    }

    @Transactional(readOnly=true)
    @Override public List<E> findByField(String field, Object value) {
        if (hashOn.equals(field)) {
            return getDAO((String) value).findByField(field, value);
        }

        // have to search all shards for it
        return queryShardsList(new ShardFindByFieldTask.Factory(field, value), "findByField");
    }

    @Transactional(readOnly=true)
    @Override public List<E> findByFieldLike(String field, String value) {
        // have to search all shards for it
        return queryShardsList(new ShardFindByFieldLikeTask.Factory(field, value), "findByFieldLike");
    }

    @Transactional(readOnly=true)
    @Override public List<E> findByFieldEqualAndFieldLike(String eqField, Object eqValue, String likeField, String likeValue) {
        if (hashOn.equals(eqField)) {
            return getDAO((String) eqValue).findByFieldEqualAndFieldLike(eqField, eqValue, likeField, likeValue);
        }

        // have to search all shards for it
        return queryShardsList(new ShardFindByFieldEqualAndFieldLikeTask.Factory(eqField, eqValue, likeField, likeValue), "findByFieldEqualAndFieldLike");
    }

    @Transactional(readOnly=true)
    @Override public List<E> findByFieldIn(String field, Object[] values) {
        if (empty(values)) return new ArrayList<>();
        if (hashOn.equals(field)) {
            final List<E> found = new ArrayList<>();
            final Set<D> daos = new HashSet<>();
            for (Object value : values) daos.add(getDAO((Serializable) value));
            for (D dao : daos) found.addAll(dao.findByFieldIn(field, values));
            return found;
        }

        // have to search all shards for it
        return queryShardsList(new ShardFindByFieldInTask.Factory(field, values), "findByFieldIn");
    }

    @Transactional(readOnly=true)
    @Override public List<E> findByFieldIn(String field, Collection<?> values) {
        if (empty(values)) return new ArrayList<>();
        return findByFieldIn(field, values.toArray());
    }

    @Transactional(readOnly=true)
    public List<E> findByFields(String f1, Object v1, String f2, Object v2) {
        if (hashOn.equals(f1)) {
            return getDAO((String) v1).findByFields(f1, v1, f2, v2);
        }

        // have to search all shards for it
        return queryShardsList(new ShardFindBy2FieldsTask.Factory(f1, v1, f2, v2), "findByFields");
    }

    @Transactional(readOnly=true)
    public List<E> findByFields(String f1, Object v1, String f2, Object v2, String f3, Object v3) {
        if (hashOn.equals(f1)) {
            return getDAO((String) v1).findByFields(f1, v1, f2, v2, f3, v3);
        }

        // have to search all shards for it
        return queryShardsList(new ShardFindBy2FieldsTask.Factory(f1, v1, f2, v2), "findByFields");
    }

    public E queryShardsUnique(ShardTaskFactory<E, D, E> factory, String ctx) {
        try {
            // Start iterator tasks on all DAOs
            final List<Future<E>> futures = new ArrayList<>();
            for (D dao : getNonOverlappingDAOs()) {
                futures.add(queryWorkerPool.submit(factory.newTask(dao)));
            }

            // Wait for all iterators to finish (or for enough to finish that the rest get cancelled)
            try {
                return awaitFirst(futures, getShardQueryTimeout(ctx));
            } catch (TimeoutException e) {
                log.warn("queryShardsUnique: timed out");
                throw timeoutEx();
            }

        } finally {
            factory.cancelTasks();
        }
    }

    protected List<E> queryShardsList(ShardTaskFactory<E, D, List<E>> factory, String ctx) {
        try {
            // Start iterator tasks on all DAOs
            final List<Future<List>> futures = new ArrayList<>();
            for (D dao : getNonOverlappingDAOs()) {
                futures.add(queryWorkerPool.submit(factory.newTask(dao)));
            }

            // Wait for all iterators to finish (or for enough to finish that the rest get cancelled)
            try {
                return awaitAndCollect(futures, MAX_QUERY_RESULTS, getShardQueryTimeout(ctx));
            } catch (TimeoutException e) {
                log.warn("queryShardsList: timed out");
                throw timeoutEx();
            }

        } finally {
            for (ShardTask task : factory.getTasks()) task.cancel();
        }
    }

    public <R> List<R> search(ShardSearch search) {
        long timeout = search.hasTimeout() ? search.getTimeout() : getShardSearchTimeout();
        if (search.hasHash()) {
            final D dao = getDAO(search.getHash());
            return dao.search(search);
        } else {
            final ShardTaskFactory factory = new ShardSearchTask.Factory(search);
            try {
                // Start iterator tasks on all DAOs
                final List<Future<List>> futures = new ArrayList<>();
                for (D dao : getNonOverlappingDAOs()) {
                    futures.add(queryWorkerPool.submit(factory.newTask(dao)));
                }

                // Wait for all iterators to finish (or for enough to finish that the rest get cancelled)
                final List<R> results;
                try {
                    final List unsorted = search.getCollector().await(futures, timeout);
                    results = search.sort(unsorted);
                } catch (TimeoutException e) {
                    log.warn("search: timed out");
                    throw timeoutEx();
                }
                return results;

            } finally {
                for (Object task : factory.getTasks()) ((ShardTask) task).cancel();
            }
        }
    }

    @Transactional(readOnly=true)
    @Override public boolean exists(String uuid) { return get(uuid) != null; }

    @Override public Object preCreate(@Valid E entity) { return null; }

    @Override public E create(@Valid E entity) {
        entity.beforeCreate();
        E rval = null;
        Object ctx = null;
        for (D dao : getDAOs(entity, ShardIO.write)) {
            if (ctx == null) {
                ctx = preCreate(entity);
            }
            E created = dao.create(entity);
            if (rval == null) {
                rval = created;
                postCreate(entity, ctx);
            }
        }
        return rval;
    }

    @Override public E createOrUpdate(@Valid E entity) {
        return (entity.getUuid() == null) ? create(entity) : update(entity);
    }

    @Override public E postCreate(E entity, Object context) {
        flushShardCache(entity.getUuid());
        return entity;
    }

    @Override public Object preUpdate(@Valid E entity) { return null; }

    @Override public E update(@Valid E entity) {
        E rval = null;
        Object ctx = null;
        for (D dao : getDAOs(entity, ShardIO.write)) {
            if (ctx == null) {
                ctx = preUpdate(entity);
                if (ctx == null) ctx = new Object();
            }
            E updated = dao.update(entity);
            if (rval == null) {
                rval = updated;
                postUpdate(entity, ctx);
            }
        }
        return rval;
    }

    @Override public E postUpdate(E entity, Object context) {
        flushShardCache(entity.getUuid());
        return entity;
    }

    @Override public void delete(String uuid) {
        final List<D> daos = hashOn.equals(UUID) ? getAllDAOs(uuid) : getAllDAOs();
        for (D dao : daos) dao.delete(uuid);
        flushShardCache(uuid);
    }

    @Override public void delete(Collection<E> entities) {
        for (E entity : entities) delete(entity.getUuid());
    }

    public void deleteAll (String hashField, String value) {
        final List<D> daos = hashOn.equals(hashField) ? getAllDAOs(value) : getAllDAOs();
        for (D dao : daos) {
            dao.getHibernateTemplate().bulkUpdate(bulkDelete(hashField), value);
        }
    }

    protected String bulkDelete(String hashField) {
        return "DELETE " + getEntityClass().getSimpleName() + " x WHERE x." + hashField + " = ?";
    }

    public void flushShardCache(String uuid) {
        flushCacheRefs(getCacheRefsKey(uuid));
        flushCacheRefs(getCacheRefsKey(NULL_CACHE));
    }

    public void flushCacheRefs(String cacheRefsKey) {
        if (cacheRefsKey == null) return;
        final List<String> cacheRefs = getShardCache().list(cacheRefsKey);
        getShardCache().del(cacheRefsKey);
        if (!empty(cacheRefs)) for (String ref : cacheRefs) getShardCache().del(ref);
        getShardCache().del(cacheRefsKey);
    }

    public String getCacheRefsKey(String uuid) { return getShardConfiguration().getName()+":cache-refs:"+uuid; }

}
