package org.cobbzilla.wizard.cache.redis;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.json.JsonUtil.fromJsonOrDie;
import static org.cobbzilla.util.json.JsonUtil.toJsonOrDie;
import static org.cobbzilla.util.security.CryptoUtil.string_decrypt;
import static org.cobbzilla.util.security.CryptoUtil.string_encrypt;
import static org.cobbzilla.util.system.Sleep.sleep;

@Service @NoArgsConstructor @Slf4j
public class RedisService {

    public static final byte MAX_RETRIES = 5;

    public static final String NX = "NX";
    public static final String XX = "XX";

    public static final String EX = "EX";
    public static final String PX = "PX";

    public static final String ALL_KEYS = "*";

    @Autowired @Getter @Setter private HasRedisConfiguration configuration;

    @Getter @Setter private String key;
    protected boolean hasKey () { return !empty(getKey()); }

    private final AtomicReference<Jedis> redis = new AtomicReference<>();
    private Jedis newJedis() { return new Jedis(configuration.getRedis().getHost(), configuration.getRedis().getPort()); }

    @Getter @Setter private String prefix = null;

    public RedisService(HasRedisConfiguration configuration) {
        this(configuration, configuration.getRedis().getPrefix(), configuration.getRedis().getKey());
    }

    public RedisService(HasRedisConfiguration configuration, String prefix, String key) {
        this(configuration.getRedis(), prefix, key);
    }

    public RedisService(RedisConfiguration configuration, String prefix, String key) {
        this.configuration = () -> configuration;
        this.prefix = prefix;
        this.key = key;
    }

    private Map<String, RedisService> prefixServiceCache = new ConcurrentHashMap<>();

    public RedisService prefixNamespace(String prefix) { return prefixNamespace(prefix, configuration.getRedis().getKey()); }

    public RedisService prefixNamespace(String prefix, String key) {
        RedisService r = prefixServiceCache.get(prefix);
        if (r == null) {
            String basePrefix = (this.prefix != null) ? this.prefix : configuration.getRedis().getPrefix();
            basePrefix = empty(basePrefix) ? "" : basePrefix + ".";
            r = new RedisService(configuration, basePrefix + prefix, key);
            prefixServiceCache.put(prefix, r);
        }
        return r;

    }

    public void reconnect () {
        if (log.isDebugEnabled()) log.debug("marking redis for reconnection...");
        synchronized (redis) {
            if (redis.get() != null) {
                try { redis.get().disconnect(); } catch (Exception e) {
                    log.warn("error disconnecting from redis before reconnecting: "+e);
                }
            }
            redis.set(null);
        }
    }

    private Jedis getRedis () {
        synchronized (redis) {
            if (redis.get() == null) {
                if (log.isDebugEnabled()) log.debug("connecting to redis...");
                redis.set(newJedis());
            }
        }
        return redis.get();
    }

    public <V> RedisMap<V> map (String prefix) { return map(prefix, null); }
    public <V> RedisMap<V> map (String prefix, Long duration) { return new RedisMap<>(prefix, duration, this); }

    public boolean exists(String key) { return __exists(prefix(key), 0); }
    public boolean anyExists(Collection<String> keys) {
        for (final String key : keys) {
            if (__exists(prefix(key), 0)) return true;
        }
        return false;
    }
    public boolean allExist(Collection<String> keys) {
        return keys.size() == __exists(prefix(keys).toArray(new String[keys.size()]), 0);
    }

    public <T> T getObject(String key, Class<T> clazz) {
        final String json = __get(prefix(key), 0);
        return empty(json) ? null : fromJsonOrDie(decrypt(json), clazz);
    }
    public String get(String key) { return decrypt(__get(prefix(key), 0)); }
    public String get_withPrefix(String prefixedKey) { return decrypt(__get(prefixedKey, 0)); }
    public String get_plaintext(String key) { return __get(prefix(key), 0); }

    /**
     * @param key
     * @return TTL in seconds for the given key
     */
    public int get_ttl(final String key) { return __ttl(prefix(key), 0).intValue(); }

    public <T> void setObject(String key, T thing) { __set(prefix(key), encrypt(toJsonOrDie(thing)), 0); }
    public void set(String key, String value) { __set(prefix(key), encrypt(value), 0); }
    public void set(String key, String value, String nxxx, String expx, long time) {
        __set(prefix(key), encrypt(value), buildSetParams(nxxx, expx, time), 0);
    }
    public void set(String key, String value, String expx, long time) {
        final String fullKey = prefix(key);
        final String preparedValue = encrypt(value);
        __set(fullKey, preparedValue, buildSetParams(XX, expx, time), 0);
        __set(fullKey, preparedValue, buildSetParams(NX, expx, time), 0);
    }
    public void set_plaintext(String key, String value) { __set(prefix(key), value, 0); }
    public void set_plaintext(String key, String value, String nxxx, String expx, long time) {
        __set(prefix(key), value, buildSetParams(nxxx, expx, time), 0);
    }
    public void set_plaintext(String key, String value, String expx, long time) {
        final String fullKey = prefix(key);
        __set(fullKey, value, buildSetParams(XX, expx, time), 0);
        __set(fullKey, value, buildSetParams(NX, expx, time), 0);
    }
    public void setAll(Collection<String> keys, String value, String expx, long time) {
        for (String k : keys) set(k, value, expx, time);
    }

    public Long touch(String key) { return __touch(prefix(key), 0); }
    public Long expire(String key, int ttlSeconds) { return __expire(prefix(key), ttlSeconds, 0); }
    public Long pexpire(String key, long ttlMillis) { return __pexpire(prefix(key), ttlMillis, 0); }

    public List<String> list(String key) { return lrange(key, 0, -1); }
    public Long llen(String key) { return __llen(prefix(key), 0); }
    public List<String> lrange(String key, int start, int end) { return __lrange(prefix(key), start, end, 0); }
    public void lpush(String key, String value) { __lpush(prefix(key), encrypt(value), 0); }
    public void rpush(String key, String value) { __rpush(prefix(key), encrypt(value), 0); }
    public String lpop(String key) { return decrypt(__lpop(prefix(key), 0)); }
    public String rpop(String key) { return decrypt(__rpop(prefix(key), 0)); }

    public void hset(String key, String field, String value) { __hset(prefix(key), encrypt(field), encrypt(value), 0); }
    public String hget(String key, String field) { return decrypt(__hget(prefix(key), encrypt(field), 0)); }
    public Map<String, String> hgetall(String key) { return decrypt(__hgetall(prefix(key), 0)); }
    public Long hdel(String key, String field) { return __hdel(prefix(key), encrypt(field), 0); }
    public Set<String> hkeys(String key) { return __hkeys(prefix(key), 0); }
    public Long hlen(String key) { return __hlen(prefix(key), 0); }

    public Long del(String key) { return __del(prefix(key), 0); }
    public Long del_withPrefix(String prefixedKey) { return __del(prefixedKey, 0); }
    public Long del_matching(String keyMatch) {
        Long count = 0L;
        for (final String fullKey : keys(keyMatch)) {
            count += __del(fullKey, 0);
        }
        return count;
    }
    public void flush() { del_matching(ALL_KEYS); }

    public Long sadd(String key, String value) { return __sadd(prefix(key), new String[]{ encrypt(value) }, 0); }
    public Long sadd(String key, String[] values) { return __sadd(prefix(key), encrypt(values), 0); }
    public Long sadd_plaintext(String key, String value) { return __sadd(prefix(key), new String[]{ value }, 0); }
    public Long sadd_plaintext(String key, String[] values) { return __sadd(prefix(key), values, 0); }
    public Long srem(String key, String value) { return __srem(prefix(key), new String[]{ encrypt(value) }, 0); }
    public Long srem(String key, String[] values) { return __srem(prefix(key), encrypt(values), 0); }
    public Set<String> smembers(String key) { return decrypt(__smembers(prefix(key), 0)); }
    public boolean sismember(String key, String value) { return __sismember(prefix(key), encrypt(value), 0); }
    public String srandmember(String key) { return decrypt(__srandmember(prefix(key), 0)); }
    public List<String> srandmembers(String key, int count) { return decrypt(__srandmember(prefix(key), count, 0)); }
    public String spop(String key) { return decrypt(__spop(prefix(key), 0)); }
    public Set<String> spop(String key, long count) { return decrypt(__spop(prefix(key), count, 0)); }
    public long scard(String key) { return __scard(prefix(key), 0); }
    public Set<String> sunion(Collection<String> keys) {
        return decrypt(__sunion(prefix(keys).toArray(new String[keys.size()]), 0));
    }
    public Set<String> sunion_plaintext(Collection<String> keys) {
        return __sunion(prefix(keys).toArray(new String[keys.size()]), 0);
    }
    public Long sunionstore(String destKey, Collection<String> keys) {
        return __sunionstore(prefix(destKey), prefix(keys).toArray(new String[keys.size()]), 0);
    }

    public Long incr(String key) { return __incrBy(prefix(key), 1, 0); }
    public Long incrBy(String key, long value) { return __incrBy(prefix(key), value, 0); }
    public Long decr(String key) { return __decrBy(prefix(key), 1, 0); }
    public Long decrBy(String key, long value) { return __decrBy(prefix(key), value, 0); }
    public Long counterValue(String key) {
        final String value = get_plaintext(key);
        return value == null ? null : Long.parseLong(value);
    }

    public Collection<String> keys(String key) { return __keys(prefix(key), 0); }
    public String rename(String key, String newFullKey) { return __rename(prefix(key), newFullKey, 0); }

    public static final String LOCK_SUFFIX = "._lock";

    public boolean confirmLock(String key, String lock, long lockTimeout) {
        key = key + LOCK_SUFFIX;
        final String lockVal = get(key);
        if (lockVal != null && lockVal.equals(lock)) {
            pexpire(key, lockTimeout);
            return true;
        }
        return false;
    }
    public String lock(String key, long lockTimeout, long deadlockTimeout) {
        if (log.isDebugEnabled()) log.debug("lock("+key+") starting");
        key = key + LOCK_SUFFIX;
        final String uuid = UUID.randomUUID().toString();
        String lockVal = get(key);
        final long start = now();
        while ((lockVal == null || !lockVal.equals(uuid)) && (now() - start < lockTimeout)) {
            set(key, uuid, NX, EX, deadlockTimeout/1000);
            if (log.isDebugEnabled()) log.debug("lock("+key+") locked with uuid="+uuid);
            lockVal = get(key);
            if (log.isDebugEnabled()) log.debug("lock("+key+") after locking with uuid="+uuid+", lockVal="+lockVal);
        }
        if (lockVal == null || !lockVal.equals(uuid)) {
            return die("lock: timeout locking "+key);
        }
        if (log.isDebugEnabled()) log.debug("lock: LOCKED "+key+" = "+lockVal);
        return uuid;
    }
    public void unlock(String key, String lock) {
        key = key + LOCK_SUFFIX;
        final String lockVal = get(key);
        if (lockVal == null || !lockVal.equals(lock)) {
            log.warn("unlock: already unlocked! "+key);
        } else {
            del(key);
            log.info("unlock: UNLOCKED "+key);
        }
    }

    public String loadScript(String script) { return __loadScript(script, 0); }
    public Object eval(String scriptsha, List<String> keys, List<String> args) {
        return __eval(scriptsha, prefix(keys), args, 0);
    }

    public String prefix (String key) { return empty(prefix) ? key : prefix + "." + key; }
    public List<String> prefix(final Collection<String> keys) {
        if (keys == null) return null;
        return keys.stream().map(this::prefix).collect(Collectors.toList());
    }

    // override these for full control
    protected String encrypt(String data) {
        if (!hasKey() || empty(data)) return data;
        return string_encrypt(data, getKey());
    }
    protected String[] encrypt(String[] data) {
        if (!hasKey() || empty(data)) return data;
        final String[] encrypted = new String[data.length];
        for (int i=0; i<data.length; i++) {
            encrypted[i] = string_encrypt(data[i], getKey());
        }
        return encrypted;
    }

    protected String decrypt(String data) {
        if (!hasKey() || empty(data)) return data;
        return string_decrypt(data, getKey());
    }
    protected <T extends Collection<String>> T decrypt(T data) {
        if (!hasKey() || empty(data)) return data;
        final T decrypted = (T) new ArrayList<String>();
        for (String value : data) {
            decrypted.add(string_decrypt(value, getKey()));
        }
        return decrypted;
    }
    protected String[] decrypt(String[] data) {
        if (!hasKey() || empty(data)) return data;
        final String[] decrypted = new String[data.length];
        for (int i=0; i<data.length; i++) {
            decrypted[i] = string_decrypt(data[i], getKey());
        }
        return decrypted;
    }
    protected Map<String, String> decrypt(Map<String, String> map) {
        if (!hasKey() || empty(map)) return map;
        final Map<String, String> decrypted = new HashMap<>(map.size());
        for (Map.Entry<String, String> entry : map.entrySet()) {
            decrypted.put(decrypt(entry.getKey()), decrypt(entry.getValue()));
        }
        return decrypted;
    }

    private SetParams buildSetParams(final String nxxx, final String expx, final long time) {
        SetParams setParams = new SetParams();
        switch (nxxx) {
            case NX: setParams.nx(); break;
            case XX: setParams.xx(); break;
        }
        switch (expx) {
            case EX: setParams.ex((int) time); break;
            case PX: setParams.px(time); break;
        }
        return setParams;
    }

    private void resetForRetry(int attempt, String reason) {
        reconnect();
        sleep(attempt * 10, reason);
    }

    private String __get(final String fullKey, final int attempt) {
        try {
            synchronized (redis) {
                return getRedis().get(fullKey);
            }
        } catch (RuntimeException e) {
            if (attempt > MAX_RETRIES) throw e;
            resetForRetry(attempt, "retrying RedisService.__get");
            return __get(fullKey, attempt + 1);
        }
    }

    private Long __ttl(final String fullKey, final int attempt) {
        try {
            synchronized (redis) {
                return getRedis().ttl(fullKey);
            }
        } catch (RuntimeException e) {
            if (attempt > MAX_RETRIES) throw e;
            resetForRetry(attempt, "retrying RedisService.__ttl");
            return __ttl(fullKey, attempt + 1);
        }
    }

    private boolean __exists(final String fullKey, final int attempt) {
        try {
            synchronized (redis) {
                return getRedis().exists(fullKey);
            }
        } catch (RuntimeException e) {
            if (attempt > MAX_RETRIES) throw e;
            resetForRetry(attempt, "retrying RedisService.__exists");
            return __exists(fullKey, attempt + 1);
        }
    }

    private Long __exists(final String[] fullKeys, final int attempt) {
        try {
            synchronized (redis) {
                return getRedis().exists(fullKeys);
            }
        } catch (RuntimeException e) {
            if (attempt > MAX_RETRIES) throw e;
            resetForRetry(attempt, "retrying RedisService.__exists on array of keys");
            return __exists(fullKeys, attempt + 1);
        }
    }

    private String __set(final String fullKey, final String preparedValue, final int attempt) {
        try {
            synchronized (redis) {
                return getRedis().set(fullKey, preparedValue);
            }
        } catch (RuntimeException e) {
            if (attempt > MAX_RETRIES) throw e;
            resetForRetry(attempt, "retrying RedisService.__set");
            return __set(fullKey, preparedValue, attempt + 1);
        }
    }

    private String __set(final String fullKey, final String preparedValue, final SetParams setParams,
                         final int attempt) {
        try {
            synchronized (redis) {
                return getRedis().set(fullKey, preparedValue, setParams);
            }
        } catch (RuntimeException e) {
            if (attempt > MAX_RETRIES) throw e;
            resetForRetry(attempt, "retrying RedisService.__set with params");
            return __set(fullKey, preparedValue, setParams, attempt + 1);
        }
    }

    private Long __touch(final String fullKey, final int attempt) {
        try {
            synchronized (redis) {
                return getRedis().touch(fullKey);
            }
        } catch (RuntimeException e) {
            if (attempt > MAX_RETRIES) throw e;
            resetForRetry(attempt, "retrying RedisService.__touch");
            return __touch(fullKey, attempt + 1);
        }
    }

    private Long __expire(final String fullKey, final int ttlSeconds, final int attempt) {
        try {
            synchronized (redis) {
                return getRedis().expire(fullKey, ttlSeconds);
            }
        } catch (RuntimeException e) {
            if (attempt > MAX_RETRIES) throw e;
            resetForRetry(attempt, "retrying RedisService.__expire");
            return __expire(fullKey, ttlSeconds, attempt + 1);
        }
    }

    private Long __pexpire(final String fullKey, final long ttlMillis, final int attempt) {
        try {
            synchronized (redis) {
                return getRedis().pexpire(fullKey, ttlMillis);
            }
        } catch (RuntimeException e) {
            if (attempt > MAX_RETRIES) throw e;
            resetForRetry(attempt, "retrying RedisService.__pexpire");
            return __pexpire(fullKey, ttlMillis, attempt + 1);
        }
    }

    private Long __lpush(final String fullKey, final String preparedValue, final int attempt) {
        try {
            synchronized (redis) {
                return getRedis().lpush(fullKey, preparedValue);
            }
        } catch (RuntimeException e) {
            if (attempt > MAX_RETRIES) throw e;
            resetForRetry(attempt, "retrying RedisService.__lpush");
            return __lpush(fullKey, preparedValue, attempt + 1);
        }
    }

    private String __lpop(final String fullKey, final int attempt) {
        try {
            synchronized (redis) {
                return getRedis().lpop(fullKey);
            }
        } catch (RuntimeException e) {
            if (attempt > MAX_RETRIES) throw e;
            resetForRetry(attempt, "retrying RedisService.__lpop");
            return __lpop(fullKey, attempt + 1);
        }
    }

    private Long __rpush(final String fullKey, final String preparedValue, final int attempt) {
        try {
            synchronized (redis) {
                return getRedis().rpush(fullKey, preparedValue);
            }
        } catch (RuntimeException e) {
            if (attempt > MAX_RETRIES) throw e;
            resetForRetry(attempt, "retrying RedisService.__rpush");
            return __rpush(fullKey, preparedValue, attempt + 1);
        }
    }

    private String __rpop(final String fullKey, final int attempt) {
        try {
            synchronized (redis) {
                return getRedis().rpop(fullKey);
            }
        } catch (RuntimeException e) {
            if (attempt > MAX_RETRIES) throw e;
            resetForRetry(attempt, "retrying RedisService.__rpop");
            return __rpop(fullKey, attempt + 1);
        }
    }

    private String __hget(final String fullKey, final String preparedField, final int attempt) {
        if (empty(preparedField)) return die("__hget(" + fullKey + "/): field was empty");
        try {
            synchronized (redis) {
                return getRedis().hget(fullKey, preparedField);
            }
        } catch (RuntimeException e) {
            if (attempt > MAX_RETRIES) throw e;
            resetForRetry(attempt, "retrying RedisService.__hget");
            return __hget(fullKey, preparedField, attempt + 1);
        }
    }

    private Map<String, String> __hgetall(final String fullKey, final int attempt) {
        try {
            synchronized (redis) {
                return getRedis().hgetAll(fullKey);
            }
        } catch (RuntimeException e) {
            if (attempt > MAX_RETRIES) throw e;
            resetForRetry(attempt, "retrying RedisService.__hgetall");
            return __hgetall(fullKey, attempt + 1);
        }
    }

    private Long __hset(final String fullKey, final String preparedField, final String preparedValue,
                        final int attempt) {
        try {
            synchronized (redis) {
                return getRedis().hset(fullKey, preparedField, preparedValue);
            }
        } catch (RuntimeException e) {
            if (attempt > MAX_RETRIES) throw e;
            resetForRetry(attempt, "retrying RedisService.__hset");
            return __hset(fullKey, preparedField, preparedValue, attempt + 1);
        }
    }

    private Long __hdel(final String fullKey, final String preparedField, final int attempt) {
        try {
            synchronized (redis) {
                return getRedis().hdel(fullKey, preparedField);
            }
        } catch (RuntimeException e) {
            if (attempt > MAX_RETRIES) throw e;
            resetForRetry(attempt, "retrying RedisService.__hdel");
            return __hdel(fullKey, preparedField, attempt + 1);
        }
    }

    private Set<String> __hkeys(final String fullKey, final int attempt) {
        try {
            synchronized (redis) {
                return getRedis().hkeys(fullKey);
            }
        } catch (RuntimeException e) {
            if (attempt > MAX_RETRIES) throw e;
            resetForRetry(attempt, "retrying RedisService.__hkeys");
            return __hkeys(fullKey, attempt + 1);
        }
    }

    private Long __hlen(final String fullKey, final int attempt) {
        try {
            synchronized (redis) {
                return getRedis().hlen(fullKey);
            }
        } catch (RuntimeException e) {
            if (attempt > MAX_RETRIES) throw e;
            resetForRetry(attempt, "retrying RedisService.__hlen");
            return __hlen(fullKey, attempt + 1);
        }
    }

    private Long __del(final String fullKey, final int attempt) {
        try {
            synchronized (redis) {
                return getRedis().del(fullKey);
            }
        } catch (RuntimeException e) {
            if (attempt > MAX_RETRIES) throw e;
            resetForRetry(attempt, "retrying RedisService.__del");
            return __del(fullKey, attempt + 1);
        }
    }

    private Long __sadd(final String fullKey, final String[] preparedMembers, final int attempt) {
        try {
            synchronized (redis) {
                return getRedis().sadd(fullKey, preparedMembers);
            }
        } catch (RuntimeException e) {
            if (attempt > MAX_RETRIES) throw e;
            resetForRetry(attempt, "retrying RedisService.__sadd");
            return __sadd(fullKey, preparedMembers, attempt + 1);
        }
    }

    private Long __srem(final String fullKey, final String[] preparedMembers, final int attempt) {
        try {
            synchronized (redis) {
                return getRedis().srem(fullKey, preparedMembers);
            }
        } catch (RuntimeException e) {
            if (attempt > MAX_RETRIES) throw e;
            resetForRetry(attempt, "retrying RedisService.__srem");
            return __srem(fullKey, preparedMembers, attempt + 1);
        }
    }

    private Set<String> __smembers(final String fullKey, final int attempt) {
        try {
            synchronized (redis) {
                return getRedis().smembers(fullKey);
            }
        } catch (RuntimeException e) {
            if (attempt > MAX_RETRIES) throw e;
            resetForRetry(attempt, "retrying RedisService.__smembers");
            return __smembers(fullKey, attempt + 1);
        }
    }

    private boolean __sismember(final String fullKey, final String preparedValue, final int attempt) {
        try {
            synchronized (redis) {
                return getRedis().sismember(fullKey, preparedValue);
            }
        } catch (RuntimeException e) {
            if (attempt > MAX_RETRIES) throw e;
            resetForRetry(attempt, "retrying RedisService.__sismember");
            return __sismember(fullKey, preparedValue, attempt + 1);
        }
    }

    private String __srandmember(final String fullKey, final int attempt) {
        try {
            synchronized (redis) {
                return getRedis().srandmember(fullKey);
            }
        } catch (RuntimeException e) {
            if (attempt > MAX_RETRIES) throw e;
            resetForRetry(attempt, "retrying RedisService.__srandmember");
            return __srandmember(fullKey, attempt + 1);
        }
    }

    private List<String> __srandmember(final String fullKey, final int count, final int attempt) {
        try {
            synchronized (redis) {
                return getRedis().srandmember(fullKey, count);
            }
        } catch (RuntimeException e) {
            if (attempt > MAX_RETRIES) throw e;
            resetForRetry(attempt, "retrying RedisService.__srandmember with count");
            return __srandmember(fullKey, count, attempt + 1);
        }
    }

    private String __spop(final String fullKey, final int attempt) {
        try {
            synchronized (redis) {
                return getRedis().spop(fullKey);
            }
        } catch (RuntimeException e) {
            if (attempt > MAX_RETRIES) throw e;
            resetForRetry(attempt, "retrying RedisService.__spop");
            return __spop(fullKey, attempt + 1);
        }
    }

    private Set<String> __spop(final String fullKey, final long count, final int attempt) {
        try {
            synchronized (redis) {
                return getRedis().spop(fullKey, count);
            }
        } catch (RuntimeException e) {
            if (attempt > MAX_RETRIES) throw e;
            resetForRetry(attempt, "retrying RedisService.__spop with count");
            return __spop(fullKey, count, attempt + 1);
        }
    }

    private Long __scard(final String fullKey, final int attempt) {
        try {
            synchronized (redis) {
                return getRedis().scard(fullKey);
            }
        } catch (RuntimeException e) {
            if (attempt > MAX_RETRIES) throw e;
            resetForRetry(attempt, "retrying RedisService.__scard");
            return __scard(fullKey, attempt + 1);
        }
    }

    private Set<String> __sunion(final String[] fullKeys, final int attempt) {
        try {
            synchronized (redis) {
                return getRedis().sunion(fullKeys);
            }
        } catch (RuntimeException e) {
            if (attempt > MAX_RETRIES) throw e;
            resetForRetry(attempt, "retrying RedisService.__sunion");
            return __sunion(fullKeys, attempt + 1);
        }
    }

    private Long __sunionstore(final String fullDestKey, final String[] fullKeys, final int attempt) {
        try {
            synchronized (redis) {
                return getRedis().sunionstore(fullDestKey, fullKeys);
            }
        } catch (RuntimeException e) {
            if (attempt > MAX_RETRIES) throw e;
            resetForRetry(attempt, "retrying RedisService.__sunionstore");
            return __sunionstore(fullDestKey, fullKeys, attempt + 1);
        }
    }

    private Long __incrBy(final String fullKey, final long value, final int attempt) {
        try {
            synchronized (redis) {
                return getRedis().incrBy(fullKey, value);
            }
        } catch (RuntimeException e) {
            if (attempt > MAX_RETRIES) throw e;
            resetForRetry(attempt, "retrying RedisService.__incrBy");
            return __incrBy(fullKey, value, attempt + 1);
        }
    }

    private Long __decrBy(final String fullKey, final long value, final int attempt) {
        try {
            synchronized (redis) {
                return getRedis().decrBy(fullKey, value);
            }
        } catch (RuntimeException e) {
            if (attempt > MAX_RETRIES) throw e;
            resetForRetry(attempt, "retrying RedisService.__decrBy");
            return __decrBy(fullKey, value, attempt + 1);
        }
    }

    private Long __llen(final String fullKey, final int attempt) {
        try {
            synchronized (redis) {
                return getRedis().llen(fullKey);
            }
        } catch (RuntimeException e) {
            if (attempt > MAX_RETRIES) throw e;
            resetForRetry(attempt, "retrying RedisService.__llen");
            return __llen(fullKey, attempt + 1);
        }
    }

    private List<String> __lrange(final String fullKey, final int start, final int end, final int attempt) {
        try {
            synchronized (redis) {
                return decrypt(getRedis().lrange(fullKey, start, end));
            }
        } catch (RuntimeException e) {
            if (attempt > MAX_RETRIES) throw e;
            resetForRetry(attempt, "retrying RedisService.__lrange");
            return __lrange(fullKey, start, end, attempt + 1);
        }
    }

    private Collection<String> __keys(final String prefixedKeyPattern, final int attempt) {
        try {
            synchronized (redis) {
                return getRedis().keys(prefixedKeyPattern);
            }
        } catch (RuntimeException e) {
            if (attempt > MAX_RETRIES) throw e;
            resetForRetry(attempt, "retrying RedisService.__keys");
            return __keys(prefixedKeyPattern, attempt + 1);
        }
    }

    private String __rename(final String fullKey, final String newFullKey, final int attempt) {
        try {
            synchronized (redis) {
                return getRedis().rename(fullKey, newFullKey);
            }
        } catch (RuntimeException e) {
            if (attempt > MAX_RETRIES) throw e;
            resetForRetry(attempt, "retrying RedisService.__rename");
            return __rename(fullKey, newFullKey, attempt + 1);
        }
    }

    private String __loadScript(final String script, final int attempt) {
        try {
            synchronized (redis) {
                return getRedis().scriptLoad(script);
            }
        } catch (RuntimeException e) {
            if (attempt > MAX_RETRIES) throw e;
            resetForRetry(attempt, "retrying RedisService.__loadScript");
            return __loadScript(script, attempt + 1);
        }
    }

    private Object __eval(final String scriptsha, final List<String> fullKeys, final List<String> args,
                          final int attempt) {
        try {
            synchronized (redis) {
                return getRedis().evalsha(scriptsha, fullKeys, args);
            }
        } catch (RuntimeException e) {
            if (attempt > MAX_RETRIES) throw e;
            resetForRetry(attempt, "retrying RedisService.__eval");
            return __eval(scriptsha, fullKeys, args, attempt + 1);
        }
    }

}
