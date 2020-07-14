package org.cobbzilla.wizard.cache.redis;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.SingletonSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static net.sf.cglib.core.CollectionUtils.transform;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.util.json.JsonUtil.fromJsonOrDie;
import static org.cobbzilla.util.json.JsonUtil.toJsonOrDie;
import static org.cobbzilla.util.security.CryptoUtil.string_decrypt;
import static org.cobbzilla.util.security.CryptoUtil.string_encrypt;
import static org.cobbzilla.util.system.Sleep.sleep;

@Service @NoArgsConstructor @Slf4j
public class RedisService {

    public static final int MAX_RETRIES = 5;

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

    public boolean exists(String key) { return __exists(key, 0, MAX_RETRIES); }

    public boolean anyExists(Collection<String> keys) {
        for (String k : keys) if (exists(k)) return true;
        return false;
    }

    public boolean allExist(Collection<String> keys) {
        for (String k : keys) if (!exists(k)) return false;
        return true;
    }

    public <T> T getObject(String key, Class<T> clazz) {
        final String json = get(key);
        return empty(json) ? null : fromJsonOrDie(json, clazz);
    }

    public String get(String key) { return decrypt(__get(key, 0, MAX_RETRIES)); }
    public String get_withPrefix(String prefixedKey) { return decrypt(__get(prefixedKey, 0, MAX_RETRIES, false)); }

    public String get_plaintext(String key) { return __get(key, 0, MAX_RETRIES); }

    public void set(String key, String value, String nxxx, String expx, long time) {
        __set(key, value, nxxx, expx, time, 0, MAX_RETRIES);
    }

    public void set(String key, String value, String expx, long time) {
        __set(key, value, XX, expx, time, 0, MAX_RETRIES);
        __set(key, value, NX, expx, time, 0, MAX_RETRIES);
    }

    public void set(String key, String value) { __set(key, value, 0, MAX_RETRIES); }

    public void set_plaintext(String key, String value, String nxxx, String expx, long time) {
        __set_plaintext(key, value, nxxx, expx, time, 0, MAX_RETRIES);
    }

    public void set_plaintext(String key, String value, String expx, long time) {
        __set_plaintext(key, value, XX, expx, time, 0, MAX_RETRIES);
        __set_plaintext(key, value, NX, expx, time, 0, MAX_RETRIES);
    }

    public void set_plaintext(String key, String value) { __set_plaintext(key, value, 0, MAX_RETRIES); }

    public void setAll(Collection<String> keys, String value, String expx, long time) {
        for (String k : keys) set(k, value, expx, time);
    }

    public <T> void setObject(String key, T thing) { __set(key, toJsonOrDie(thing), 0, MAX_RETRIES); }

    public Long touch(String key) { return __touch(key, 0, MAX_RETRIES); }
    public Long expire(String key, long ttl) { return __expire(key, (int) ttl, 0, MAX_RETRIES); }

    public List<String> list(String key) { return lrange(key, 0, -1); }
    public Long llen(String key) { return __llen(key, 0, MAX_RETRIES); }
    public List<String> lrange(String key, int start, int end) { return __lrange(key, start, end, 0, MAX_RETRIES); }
    public void lpush(String key, String value) { __lpush(key, value, 0, MAX_RETRIES); }
    public String lpop(String data) { return decrypt(__lpop(data, 0, MAX_RETRIES)); }
    public void rpush(String key, String value) { __rpush(key, value, 0, MAX_RETRIES); }
    public String rpop(String data) { return decrypt(__rpop(data, 0, MAX_RETRIES)); }

    public void hset(String key, String field, String value) { __hset(key, field, value, 0, MAX_RETRIES); }
    public String hget(String key, String field) { return decrypt(__hget(key, field, 0, MAX_RETRIES)); }
    public Map<String, String> hgetall(String key) { return decrypt(__hgetall(key, 0, MAX_RETRIES)); }

    private Map<String, String> decrypt(Map<String, String> map) {
        if (!hasKey()) return map;
        final Map<String, String> decrypted = new HashMap<>();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            decrypted.put(decrypt(entry.getKey()), decrypt(entry.getValue()));
        }
        return decrypted;
    }

    public Long hdel(String key, String field) { return __hdel(key, field, 0, MAX_RETRIES); }
    public Set<String> hkeys(String key) { return __hkeys(key, 0, MAX_RETRIES); }
    public Long hlen(String key) { return __hlen(key, 0, MAX_RETRIES); }

    public Long del(String key) { return __del(key, 0, MAX_RETRIES); }
    public Long del_withPrefix(String prefixedKey) { return __del(prefixedKey, 0, MAX_RETRIES, false); }

    public Long del_matching(String keyMatch) {
        Long count = 0L;
        for (String key : keys(keyMatch)) {
            count += del_withPrefix(key);
        }
        return count;
    }

    public Long sadd(String key, String value) { return sadd(key, new String[]{value}); }
    public Long sadd(String key, String[] values) { return __sadd(key, values, 0, MAX_RETRIES); }

    public Long sadd_plaintext(String key, String value) { return sadd_plaintext(key, new String[]{value}); }
    public Long sadd_plaintext(String key, String[] values) { return __sadd(key, values, 0, MAX_RETRIES, false); }

    public Long srem(String key, String value) { return srem(key, new String[]{value}); }
    public Long srem(String key, String[] values) { return __srem(key, values, 0, MAX_RETRIES); }

    public Set<String> smembers(String key) { return __smembers(key, 0, MAX_RETRIES); }
    public boolean sismember(String key, String value) { return __sismember(key, value, 0, MAX_RETRIES); }

    public List<String> srandmembers(String key, int count) { return __srandmember(key, count, 0, MAX_RETRIES); }
    public String srandmember(String key) {
        final List<String> rand = srandmembers(key, 1);
        return empty(rand) ? null : rand.get(0);
    }

    public String spop(String key) {
        final Set<String> popped = spop(key, 1);
        return empty(popped) ? null : popped.iterator().next();
    }
    public Set<String> spop(String key, long count) { return __spop(key, count, 0, MAX_RETRIES); }

    public long scard(String key) { return __scard(key, 0, MAX_RETRIES); }

    public Set<String> sunion(Collection<String> keys) { return __sunion(keys, 0, MAX_RETRIES); }
    public Set<String> sunion_plaintext(Collection<String> keys) { return __sunion(keys, 0, MAX_RETRIES, false); }

    public Long sunionstore(String destKey, Collection<String> keys) { return __sunionstore(destKey, keys, 0, MAX_RETRIES); }

    public Long incr(String key) { return __incrBy(key, 1, 0, MAX_RETRIES); }
    public Long counterValue(String key) {
        final String value = get_plaintext(key);
        return value == null ? null : Long.parseLong(value);
    }

    public Long incrBy(String key, long value) { return __incrBy(key, value, 0, MAX_RETRIES); }
    public Long decr(String key) { return __decrBy(key, 1, 0, MAX_RETRIES); }

    public Long decrBy(String key, long value) { return __decrBy(key, value, 0, MAX_RETRIES); }

    public Collection<String> keys(String key) { return __keys(key, 0, MAX_RETRIES); }

    public String rename(String key, String newKey) { return __rename(key, newKey, 0, MAX_RETRIES); }

    public static final String LOCK_SUFFIX = "._lock";

    public boolean confirmLock(String key, String lock, long lockTimeout) {
        key = key + LOCK_SUFFIX;
        final String lockVal = get(key);
        if (lockVal != null && lockVal.equals(lock)) {
            expire(key, lockTimeout);
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

    public String loadScript(String script) { return __loadScript(script, 0, MAX_RETRIES); }

    private String __loadScript(String script, int attempt, int maxRetries) {
        try {
            synchronized (redis) {
                return getRedis().scriptLoad(script);
            }
        } catch (RuntimeException e) {
            if (attempt > maxRetries) throw e;
            resetForRetry(attempt, "retrying RedisService.__loadScript");
            return __loadScript(script, attempt+1, maxRetries);
        }
    }


    public Object eval(String scriptsha, List<String> keys, List<String> args) {
        return __eval(scriptsha, prefix(keys), args, 0, MAX_RETRIES);
    }

    private Object __eval(String scriptsha, List<String> keys, List<String> args, int attempt, int maxRetries) {
        try {
            synchronized (redis) {
                return getRedis().evalsha(scriptsha, keys, args);
            }
        } catch (RuntimeException e) {
            if (attempt > maxRetries) throw e;
            resetForRetry(attempt, "retrying RedisService.__eval");
            return __eval(scriptsha, keys, args, attempt+1, maxRetries);
        }
    }

    public String prefix (String key) { return empty(prefix) ? key : prefix + "." + key; }
    public List<String> prefix(Collection<String> keys) { return transform(keys, o -> prefix(o.toString())); }

    // override these for full control
    protected String encrypt(String data) {
        if (!hasKey()) return data;
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
        if (!hasKey()) return data;
        if (data == null) return null;
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

    private void resetForRetry(int attempt, String reason) {
        reconnect();
        sleep(attempt * 10, reason);
    }

    private String __get(String key, int attempt, int maxRetries) {
        return __get(key, attempt, maxRetries, true);
    }

    private String __get(String key, int attempt, int maxRetries, boolean applyPrefix) {
        try {
            synchronized (redis) {
                return getRedis().get(applyPrefix ? prefix(key) : key);
            }
        } catch (RuntimeException e) {
            if (attempt > maxRetries) throw e;
            resetForRetry(attempt, "retrying RedisService.__get");
            return __get(key, attempt+1, maxRetries);
        }
    }

    private boolean __exists(String key, int attempt, int maxRetries) {
        try {
            synchronized (redis) {
                return getRedis().exists(prefix(key));
            }
        } catch (RuntimeException e) {
            if (attempt > maxRetries) throw e;
            resetForRetry(attempt, "retrying RedisService.__exists");
            return __exists(key, attempt+1, maxRetries);
        }
    }

    private SetParams getSetParams(String nxxx, String expx, long time) {
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

    private String __set(String key, String value, String nxxx, String expx, long time, int attempt, int maxRetries) {
        try {
            synchronized (redis) {
                return getRedis().set(prefix(key), encrypt(value), getSetParams(nxxx, expx, time));
            }
        } catch (RuntimeException e) {
            if (attempt > maxRetries) throw e;
            resetForRetry(attempt, "retrying RedisService.__set");
            return __set(key, value, nxxx, expx, time, attempt + 1, maxRetries);
        }
    }

    private String __set(String key, String value, int attempt, int maxRetries) {
        try {
            synchronized (redis) {
                return getRedis().set(prefix(key), encrypt(value));
            }
        } catch (RuntimeException e) {
            if (attempt > maxRetries) throw e;
            resetForRetry(attempt, "retrying RedisService.__set");
            return __set(key, value, attempt+1, maxRetries);
        }
    }

    private String __set_plaintext(String key, String value, String nxxx, String expx, long time, int attempt, int maxRetries) {
        try {
            synchronized (redis) {
                return getRedis().set(prefix(key), value, getSetParams(nxxx, expx, time));
            }
        } catch (RuntimeException e) {
            if (attempt > maxRetries) throw e;
            resetForRetry(attempt, "retrying RedisService.__set_plaintext");
            return __set_plaintext(key, value, nxxx, expx, time, attempt + 1, maxRetries);
        }
    }

    private String __set_plaintext(String key, String value, int attempt, int maxRetries) {
        try {
            synchronized (redis) {
                return getRedis().set(prefix(key), value);
            }
        } catch (RuntimeException e) {
            if (attempt > maxRetries) throw e;
            resetForRetry(attempt, "retrying RedisService.__set_plaintext");
            return __set_plaintext(key, value, attempt+1, maxRetries);
        }
    }

    private Long __touch(String key, int attempt, int maxRetries) {
        try {
            synchronized (redis) {
                return getRedis().touch(prefix(key));
            }
        } catch (RuntimeException e) {
            if (attempt > maxRetries) throw e;
            resetForRetry(attempt, "retrying RedisService.__touch");
            return __touch(key, attempt+1, maxRetries);
        }
    }

    private Long __expire(String key, int ttl, int attempt, int maxRetries) {
        try {
            synchronized (redis) {
                return getRedis().expire(prefix(key), ttl);
            }
        } catch (RuntimeException e) {
            if (attempt > maxRetries) throw e;
            resetForRetry(attempt, "retrying RedisService.__expire");
            return __expire(key, ttl, attempt+1, maxRetries);
        }
    }

    private Long __lpush(String key, String value, int attempt, int maxRetries) {
        try {
            synchronized (redis) {
                return getRedis().lpush(prefix(key), encrypt(value));
            }
        } catch (RuntimeException e) {
            if (attempt > maxRetries) throw e;
            resetForRetry(attempt, "retrying RedisService.__lpush");
            return __lpush(key, value, attempt + 1, maxRetries);
        }
    }

    private String __lpop(String key, int attempt, int maxRetries) {
        try {
            synchronized (redis) {
                return getRedis().lpop(prefix(key));
            }
        } catch (RuntimeException e) {
            if (attempt > maxRetries) throw e;
            resetForRetry(attempt, "retrying RedisService.__lpop");
            return __lpop(key, attempt+1, maxRetries);
        }
    }

    private Long __rpush(String key, String value, int attempt, int maxRetries) {
        try {
            synchronized (redis) {
                return getRedis().rpush(prefix(key), encrypt(value));
            }
        } catch (RuntimeException e) {
            if (attempt > maxRetries) throw e;
            resetForRetry(attempt, "retrying RedisService.__rpush");
            return __rpush(key, value, attempt + 1, maxRetries);
        }
    }

    private String __rpop(String data, int attempt, int maxRetries) {
        try {
            synchronized (redis) {
                return getRedis().rpop(data);
            }
        } catch (RuntimeException e) {
            if (attempt > maxRetries) throw e;
            resetForRetry(attempt, "retrying RedisService.__rpop");
            return __rpop(data, attempt+1, maxRetries);
        }
    }

    private String __hget(String key, String field, int attempt, int maxRetries) {
        if (empty(field)) return die("__hget("+key+"/): field was empty");
        try {
            synchronized (redis) {
                return getRedis().hget(prefix(key), encrypt(field));
            }
        } catch (RuntimeException e) {
            if (attempt > maxRetries) throw e;
            resetForRetry(attempt, "retrying RedisService.__hget");
            return __hget(key, field, attempt + 1, maxRetries);
        }
    }

    private Map<String, String> __hgetall(String key, int attempt, int maxRetries) {
        try {
            synchronized (redis) {
                return getRedis().hgetAll(prefix(key));
            }
        } catch (RuntimeException e) {
            if (attempt > maxRetries) throw e;
            resetForRetry(attempt, "retrying RedisService.__hgetall");
            return __hgetall(key, attempt + 1, maxRetries);
        }
    }

    private Long __hset(String key, String field, String value, int attempt, int maxRetries) {
        try {
            synchronized (redis) {
                return getRedis().hset(prefix(key), encrypt(field), encrypt(value));
            }
        } catch (RuntimeException e) {
            if (attempt > maxRetries) throw e;
            resetForRetry(attempt, "retrying RedisService.__hget");
            return __hset(key, field, value, attempt + 1, maxRetries);
        }
    }

    private Long __hdel(String key, String field, int attempt, int maxRetries) {
        try {
            synchronized (redis) {
                return getRedis().hdel(prefix(key), encrypt(field));
            }
        } catch (RuntimeException e) {
            if (attempt > maxRetries) throw e;
            resetForRetry(attempt, "retrying RedisService.__hdel");
            return __hdel(key, field, attempt + 1, maxRetries);
        }
    }

    private Set<String> __hkeys(String key, int attempt, int maxRetries) {
        try {
            synchronized (redis) {
                return getRedis().hkeys(prefix(key));
            }
        } catch (RuntimeException e) {
            if (attempt > maxRetries) throw e;
            resetForRetry(attempt, "retrying RedisService.__hget");
            return __hkeys(key, attempt + 1, maxRetries);
        }
    }

    private Long __hlen(String key, int attempt, int maxRetries) {
        try {
            synchronized (redis) {
                return getRedis().hlen(prefix(key));
            }
        } catch (RuntimeException e) {
            if (attempt > maxRetries) throw e;
            resetForRetry(attempt, "retrying RedisService.__hlen");
            return __hlen(key, attempt + 1, maxRetries);
        }
    }

    private Long __del(String key, int attempt, int maxRetries) {
        return __del(key, attempt, maxRetries, true);
    }

    private Long __del(String key, int attempt, int maxRetries, boolean applyPrefix) {
        try {
            synchronized (redis) {
                return getRedis().del(applyPrefix ? prefix(key) : key);
            }
        } catch (RuntimeException e) {
            if (attempt > maxRetries) throw e;
            resetForRetry(attempt, "retrying RedisService.__del");
            return __del(key, attempt+1, maxRetries, applyPrefix);
        }
    }

    private Long __sadd(String key, String[] members, int attempt, int maxRetries) {
        return __sadd(key, members, attempt, maxRetries, true);
    }

    private Long __sadd(String key, String[] members, int attempt, int maxRetries, boolean crypt) {
        try {
            synchronized (redis) {
                return getRedis().sadd(prefix(key), crypt ? encrypt(members) : members);
            }
        } catch (RuntimeException e) {
            if (attempt > maxRetries) throw e;
            resetForRetry(attempt, "retrying RedisService.__sadd");
            return __sadd(key, members, attempt+1, maxRetries);
        }
    }

    private Long __srem(String key, String[] members, int attempt, int maxRetries) {
        try {
            synchronized (redis) {
                return getRedis().srem(prefix(key), encrypt(members));
            }
        } catch (RuntimeException e) {
            if (attempt > maxRetries) throw e;
            resetForRetry(attempt, "retrying RedisService.__srem");
            return __srem(key, members, attempt+1, maxRetries);
        }
    }

    private Set<String> __smembers(String key, int attempt, int maxRetries) {
        try {
            synchronized (redis) {
                return decrypt(getRedis().smembers(prefix(key)));
            }
        } catch (RuntimeException e) {
            if (attempt > maxRetries) throw e;
            resetForRetry(attempt, "retrying RedisService.__smembers");
            return __smembers(key, attempt+1, maxRetries);
        }
    }

    private boolean __sismember(String key, String value, int attempt, int maxRetries) {
        try {
            synchronized (redis) {
                return getRedis().sismember(prefix(key), encrypt(value));
            }
        } catch (RuntimeException e) {
            if (attempt > maxRetries) throw e;
            resetForRetry(attempt, "retrying RedisService.__sismember");
            return __sismember(key, value, attempt+1, maxRetries);
        }
    }

    private List<String> __srandmember(String key, int count, int attempt, int maxRetries) {
        try {
            synchronized (redis) {
                return decrypt(getRedis().srandmember(prefix(key), count));
            }
        } catch (RuntimeException e) {
            if (attempt > maxRetries) throw e;
            resetForRetry(attempt, "retrying RedisService.__srandmember");
            return __srandmember(key, count, attempt+1, maxRetries);
        }
    }

    private Set<String> __spop(String key, long count, int attempt, int maxRetries) {
        try {
            synchronized (redis) {
                return decrypt(count == 1 ? new SingletonSet<>(getRedis().spop(prefix(key))) : getRedis().spop(prefix(key), count));
            }
        } catch (RuntimeException e) {
            if (attempt > maxRetries) throw e;
            resetForRetry(attempt, "retrying RedisService.__spop");
            return __spop(key, count, attempt+1, maxRetries);
        }
    }

    private Long __scard(String key, int attempt, int maxRetries) {
        try {
            synchronized (redis) {
                return getRedis().scard(prefix(key));
            }
        } catch (RuntimeException e) {
            if (attempt > maxRetries) throw e;
            resetForRetry(attempt, "retrying RedisService.__scard");
            return __scard(key, attempt+1, maxRetries);
        }
    }

    private Set<String> __sunion(Collection<String> keys, int attempt, int maxRetries) {
        return __sunion(keys, attempt, maxRetries, true);
    }
    private Set<String> __sunion(Collection<String> keys, int attempt, int maxRetries, boolean crypt) {
        try {
            String[] prefixedKeys = keys.stream().map(this::prefix).toArray(String[]::new);
            synchronized (redis) {
                final Set<String> values = getRedis().sunion(prefixedKeys);
                return crypt ? values.stream().map(this::decrypt).collect(Collectors.toSet()) : values;
            }
        } catch (RuntimeException e) {
            if (attempt > maxRetries) throw e;
            resetForRetry(attempt, "retrying RedisService.__sunion");
            return __sunion(keys, attempt+1, maxRetries);
        }
    }

    private Long __sunionstore(String destKey, Collection<String> keys, int attempt, int maxRetries) {
        try {
            String[] prefixedKeys = keys.stream().map(this::prefix).toArray(String[]::new);
            synchronized (redis) {
                return getRedis().sunionstore(prefix(destKey), prefixedKeys);
            }
        } catch (RuntimeException e) {
            if (attempt > maxRetries) throw e;
            resetForRetry(attempt, "retrying RedisService.__sunionstore");
            return __sunionstore(destKey, keys, attempt+1, maxRetries);
        }
    }

    private Long __incrBy(String key, long value, int attempt, int maxRetries) {
        try {
            synchronized (redis) {
                return getRedis().incrBy(prefix(key), value);
            }
        } catch (RuntimeException e) {
            if (attempt > maxRetries) throw e;
            resetForRetry(attempt, "retrying RedisService.__incrBy");
            return __incrBy(key, value, attempt + 1, maxRetries);
        }
    }

    private Long __decrBy(String key, long value, int attempt, int maxRetries) {
        try {
            synchronized (redis) {
                return getRedis().decrBy(prefix(key), value);
            }
        } catch (RuntimeException e) {
            if (attempt > maxRetries) throw e;
            resetForRetry(attempt, "retrying RedisService.__decrBy");
            return __decrBy(key, value, attempt+1, maxRetries);
        }
    }

    private Long __llen(String key, int attempt, int maxRetries) {
        try {
            synchronized (redis) {
                return getRedis().llen(prefix(key));
            }

        } catch (RuntimeException e) {
            if (attempt > maxRetries) throw e;
            resetForRetry(attempt, "retrying RedisService.__llen");
            return __llen(key, attempt + 1, maxRetries);
        }
    }

    private List<String> __lrange(String key, int start, int end, int attempt, int maxRetries) {
        try {
            final List<String> range;
            synchronized (redis) {
                range = getRedis().lrange(prefix(key), start, end);
            }
            final List<String> list = new ArrayList<>(range.size());
            for (String item : range) list.add(decrypt(item));

            return list;

        } catch (RuntimeException e) {
            if (attempt > maxRetries) throw e;
            resetForRetry(attempt, "retrying RedisService.__lrange");
            return __lrange(key, start, end, attempt + 1, maxRetries);
        }
    }

    private Collection<String> __keys(String key, int attempt, int maxRetries) {
        try {
            final Set<String> keys;
            synchronized (redis) {
                keys = getRedis().keys(prefix(key));
            }
            return keys;

        } catch (RuntimeException e) {
            if (attempt > maxRetries) throw e;
            resetForRetry(attempt, "retrying RedisService.__keys");
            return __keys(key, attempt + 1, maxRetries);
        }
    }

    private String __rename(String key, String newKey, int attempt, int maxRetries) {
        try {
            final String rval;
            synchronized (redis) {
                rval = getRedis().rename(prefix(key), newKey);
            }
            return rval;

        } catch (RuntimeException e) {
            if (attempt > maxRetries) throw e;
            resetForRetry(attempt, "retrying RedisService.__rename");
            return __rename(key, newKey, attempt + 1, maxRetries);
        }
    }

    public void flush() { del_matching(ALL_KEYS); }

}
