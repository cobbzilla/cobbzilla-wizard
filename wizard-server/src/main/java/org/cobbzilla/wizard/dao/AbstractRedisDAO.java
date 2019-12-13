package org.cobbzilla.wizard.dao;

import lombok.Getter;
import org.cobbzilla.wizard.cache.redis.RedisService;
import org.cobbzilla.wizard.model.ExpirableBase;
import org.cobbzilla.wizard.model.search.ResultPage;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.Valid;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import static org.cobbzilla.util.daemon.ZillaRuntime.notSupported;
import static org.cobbzilla.util.json.JsonUtil.fromJsonOrDie;
import static org.cobbzilla.util.json.JsonUtil.toJsonOrDie;
import static org.cobbzilla.util.reflect.ReflectionUtil.getTypeParameter;

public abstract class AbstractRedisDAO<E extends ExpirableBase> implements DAO<E> {

    @Getter private final Class<E> entityClass;

    public AbstractRedisDAO () { this.entityClass = (Class<E>) getTypeParameter(getClass()); }

    @Autowired private RedisService redis;
    @Getter(lazy=true) private final RedisService prefixRedis = initPrefixRedis();
    protected RedisService initPrefixRedis() { return redis.prefixNamespace(getRedisKeyPrefix(), getRedisEncryptionKey()); }
    private RedisService getRedis () { return getPrefixRedis(); }

    protected String getRedisKeyPrefix() { return entityClass.getSimpleName(); }
    protected String getRedisEncryptionKey() { return null; }

    // not supported
    @Override public SearchResults<E> search(ResultPage resultPage) { return notSupported(); }
    @Override public SearchResults<E> search(ResultPage resultPage, String entityAlias) { return notSupported(); }
    @Override public E findByUniqueField(String field, Object value) { return notSupported(); }
    @Override public List<E> findByField(String field, Object value) { return notSupported(); }
    @Override public List<E> findByFieldLike(String field, String value) { return notSupported(); }
    @Override public List<E> findByFieldEqualAndFieldLike(String eqField, Object eqValue, String likeField, String likeValue) { return notSupported(); }
    @Override public List<E> findByFieldIn(String field, Object[] values) { return notSupported(); }
    @Override public List<E> findByFieldIn(String field, Collection<?> values) { return notSupported(); }

    @Override public List<E> findAll() { return notSupported(); }

    // default implementations
    @Override public Object preCreate(@Valid E entity) { if (!entity.hasUuid()) entity.initUuid(); return entity; }
    @Override public E postCreate(E entity, Object context) { return entity; }
    @Override public Object preUpdate(@Valid E entity) { return entity; }
    @Override public E postUpdate(@Valid E entity, Object context) { return entity; }

    // get something
    @Override public E get(Serializable id) {
        final String json = getRedis().get(id.toString());
        return json == null ? null : fromJsonOrDie(json, getEntityClass());
    }

    @Override public E findByUuid(String uuid) { return get(uuid); }

    @Override public boolean exists(String uuid) { return get(uuid) != null; }

    // set something
    @Override public E create(@Valid E entity) { return update(entity); }

    @Override public E update(@Valid E entity) {
        if (entity.shouldExpire()) {
            getRedis().set(entity.getUuid(), toJsonOrDie(entity), "XX", "EX", entity.getExpirationSeconds());
            getRedis().set(entity.getUuid(), toJsonOrDie(entity), "NX", "EX", entity.getExpirationSeconds());
        } else {
            getRedis().set(entity.getUuid(), toJsonOrDie(entity));
        }
        return entity;
    }

    @Override public E createOrUpdate(@Valid E entity) { return update(entity); }

    // delete something
    @Override public void delete(String uuid) { getRedis().del(uuid); }

    @Override public void delete(Collection<E> entities) {
        for (E entity : entities) getRedis().del(entity.getUuid());
    }

    public String getMetadata (String key) { return getRedis().get("__metadata_"+key); }
    public void setMetadata (String key, String value) { getRedis().set("__metadata_"+key, value); }

}
