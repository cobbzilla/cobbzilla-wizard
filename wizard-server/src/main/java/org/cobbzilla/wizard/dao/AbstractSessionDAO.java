package org.cobbzilla.wizard.dao;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.json.JsonUtil;
import org.cobbzilla.wizard.cache.redis.RedisService;
import org.cobbzilla.wizard.model.Identifiable;
import org.springframework.beans.factory.annotation.Autowired;

import static java.util.UUID.randomUUID;
import static java.util.concurrent.TimeUnit.DAYS;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.reflect.ReflectionUtil.getFirstTypeParam;
import static org.cobbzilla.wizard.cache.redis.RedisService.*;
import static org.cobbzilla.wizard.resources.ResourceUtil.forbiddenEx;

@Slf4j
public abstract class AbstractSessionDAO<T extends Identifiable> {

    @Autowired private RedisService redis;
    @Getter(lazy=true) private final RedisService sessionRedis = initSessionRedis();
    private RedisService initSessionRedis() { return redis.prefixNamespace(getClass().getSimpleName()); }

    // what are we storing?
    @Getter(lazy=true, value=AccessLevel.PROTECTED) private final Class<T> entityClass = getFirstTypeParam(getClass(), Identifiable.class);

    public String create (T thing) {
        if (!canStartSession(thing)) throw forbiddenEx();
        final String sessionId = randomUUID().toString();
        set(sessionId, thing, false);
        return sessionId;
    }

    protected boolean canStartSession(T thing) { return true; }

    public T find(String uuid) {
        if (empty(uuid)) return null;
        try {
            final String found = getSessionRedis().get(uuid);
            if (found == null) return null;
            return fromJson(found);

        } catch (Exception e) {
            log.error("Error reading from redis: " + e, e);
            return null;
        }
    }

    public void touch(String uuid, T thing) { rawSet(uuid, thing, true); }

    public void invalidateAllSessions(String uuid) {
        String sessionId;
        while ((sessionId = getSessionRedis().lpop(uuid)) != null) {
            invalidate(sessionId);
        }
        invalidate(uuid);
    }

    private void set(String uuid, T thing, boolean shouldExist) {
        rawSet(uuid, thing, shouldExist);
        getSessionRedis().lpush(thing.getUuid(), uuid);
    }

    private void rawSet(String uuid, T thing, boolean shouldExist) {
        getSessionRedis().set(uuid, toJson(thing), shouldExist ? XX : NX, EX, getSessionTimeout(thing));
    }

    private long getSessionTimeout(T thing) { return getSessionTimeout(); }

    protected long getSessionTimeout() { return DAYS.toSeconds(30); }

    // override these to keep the padding but do your own json I/O
    protected String toJson(T thing) { return JsonUtil.toJsonOrDie(thing); }
    protected T fromJson(String json) { return JsonUtil.fromJsonOrDie(json, getEntityClass()); }

    public void update(String uuid, T thing) { set(uuid, thing, true); }

    public void invalidate(String uuid) { getSessionRedis().del(uuid); }

    public boolean isValid (String uuid) { return find(uuid) != null; }

}
