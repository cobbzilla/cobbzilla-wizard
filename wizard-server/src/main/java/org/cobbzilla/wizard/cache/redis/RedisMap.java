package org.cobbzilla.wizard.cache.redis;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.daemon.ZillaRuntime.notSupported;
import static org.cobbzilla.util.json.JsonUtil.fromJsonOrDie;
import static org.cobbzilla.util.json.JsonUtil.toJsonOrDie;
import static org.cobbzilla.util.reflect.ReflectionUtil.getFirstTypeParam;
import static org.cobbzilla.wizard.cache.redis.RedisService.NX;
import static org.cobbzilla.wizard.cache.redis.RedisService.XX;

@AllArgsConstructor
public class RedisMap<V> implements Map<String, V> {

    @Getter @Setter private String prefix;
    @Getter @Setter private Long duration;
    @Getter @Setter private RedisService redis;

    @Getter(lazy=true) private final Class<V> valueClass = getFirstTypeParam(getClass());

    @Override public int size() { return notSupported("size method not supported due to performance reasons"); }
    @Override public boolean containsValue(Object value) { return notSupported("containsValue method not supported due to performance reasons"); }
    @Override public void clear() { notSupported("clear method not supported due to performance reasons"); }
    @Override public Set<String> keySet() { return notSupported("keySet method not supported due to performance reasons"); }
    @Override public Collection<V> values() { return notSupported("values method not supported due to performance reasons"); }
    @Override public Set<Entry<String, V>> entrySet() { return notSupported("entrySet method not supported due to performance reasons"); }

    @Override public boolean isEmpty() { return false; }
    @Override public boolean containsKey(Object key) { return get(key) != null; }

    public String keyName(Object key) { return prefix + key; }

    @Override public V get(Object key) {
        final String value = redis.get(keyName(key));
        return empty(value) ? null : fromJsonOrDie(value, getValueClass());
    }

    @Override public V put(String key, V value) {
        if (empty(value)) {
            redis.del(keyName(key));
        } else if (duration == null) {
            redis.set(keyName(key), toJsonOrDie(value));
        } else {
            redis.set(keyName(key), toJsonOrDie(value), NX, "PX", duration);
            redis.set(keyName(key), toJsonOrDie(value), XX, "PX", duration);
        }
        return null;
    }

    @Override public V remove(Object key) {
        redis.del(keyName(key));
        return null;
    }

    @Override public void putAll(Map<? extends String, ? extends V> m) {
        for (Map.Entry<? extends String, ? extends V> entry : m.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

}
