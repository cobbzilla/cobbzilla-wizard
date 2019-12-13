package org.cobbzilla.wizard.dao;

/**
 * Forked from dropwizard https://github.com/dropwizard/
 * https://github.com/dropwizard/dropwizard/blob/master/LICENSE
 */

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.reflect.ReflectionUtil;
import org.cobbzilla.wizard.api.CrudOperation;
import org.cobbzilla.wizard.model.AuditLog;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.server.config.RestServerConfiguration;
import org.cobbzilla.wizard.validation.ConstraintViolationBean;
import org.cobbzilla.wizard.validation.MultiViolationException;
import org.cobbzilla.wizard.validation.SimpleViolationException;
import org.cobbzilla.wizard.validation.ValidationResult;
import org.hibernate.FlushMode;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.hibernate4.HibernateTemplate;
import org.springframework.transaction.annotation.Transactional;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.json.JsonUtil.toJsonOrDie;
import static org.cobbzilla.util.reflect.ReflectionUtil.*;
import static org.cobbzilla.util.time.TimeUtil.formatDuration;
import static org.hibernate.criterion.Restrictions.*;

@Transactional @Slf4j
public abstract class AbstractCRUDDAO<E extends Identifiable>
        extends AbstractDAO<E>
        implements CacheFlushable {

    @Autowired private RestServerConfiguration serverConfiguration;

    public static final String NO_SUB_KEY = "__no_subkey";

    public <A extends AuditLog> AuditLogDAO<A> getAuditLogDAO() { return null; }
    public boolean auditingEnabled () { return getAuditLogDAO() != null; }

    @Transactional(readOnly=true)
    @Override public List<E> findAll() { return list(criteria()); }

    @Transactional(readOnly=true)
    @Override public E findByUuid(String uuid) { return findByUniqueField("uuid", uuid); }

    @Transactional(readOnly=true)
    public List<E> findByUuids(Collection<String> uuids) {
        return empty(uuids) ? new ArrayList<E>() : findByFieldIn("uuid", uuids);
    }
    @Transactional(readOnly=true)
    public List<E> findByUuids(Object[] uuids) {
        return empty(uuids) ? new ArrayList<E>() : findByFieldIn("uuid", uuids);
    }

    @Transactional(readOnly=true)
    public E findFirstByUuids(Collection<String> uuids) { return findFirstByFieldIn("uuid", uuids); }

    @Transactional(readOnly=true)
    @Override public boolean exists(String uuid) { return findByUuid(uuid) != null; }

    public void validateOrException(@Valid E entity) {
        // Note that @Valid is for java's support. The validation here will include custom validation according to
        // EntityConfig.
        final ValidationResult validationResult = serverConfiguration.getValidator().validate(entity);
        if (validationResult.isInvalid()) {
            final List<ConstraintViolationBean> violationBeans = validationResult.getViolationBeans();
            if (violationBeans.size() == 1) {
                final ConstraintViolationBean v = violationBeans.get(0);
                throw new SimpleViolationException(v.getMessageTemplate(), v.getMessage(), v.getInvalidValue());
            }
            throw new MultiViolationException(violationBeans);
        }
    }

    @Override public Object preCreate(@Valid E entity) {
        validateOrException(entity);
        try {
            return auditingEnabled() ? audit(null, entity, CrudOperation.create) : entity;
        } finally {
            flushObjectCache(entity);
        }
    }

    protected String subCacheAttribute () { return null; }

    public boolean flushObjectCache() {
        synchronized (ocache) {
            if (empty(ocache.get())) {
                return false;
            } else {
                ocache.set(new ConcurrentHashMap<>());
                return true;
            }
        }
    }

    @Override public void flush () { flushObjectCache(); }

    public void flushObjectCache(E entity) {
        synchronized (ocache) {
            if (empty(ocache.get())) return;

            final String subCacheAttr = subCacheAttribute();
            final Object val = (subCacheAttr != null) ? ReflectionUtil.get(entity, subCacheAttr) : null;

            if (val != null) {
                Map<Object, Object> subCache = (Map<Object, Object>) ocache.get().get(val);
                if (subCache != null && !subCache.isEmpty()) {
                    subCache = new ConcurrentHashMap<>();
                    ocache.get().put(val.toString(), subCache);
                }
            } else {
                ocache.set(new ConcurrentHashMap<>());
            }

            final Map globalCache = (Map) ocache.get().get(NO_SUB_KEY);
            if (!empty(globalCache)) {
                ocache.get().put(NO_SUB_KEY, new ConcurrentHashMap<>());
            }
        }
    }

    @Override public E postCreate(E entity, Object context) {
        return auditingEnabled() ? commit_audit(entity, context) : entity;
    }

    @Override public E create(@Valid E entity) { return AbstractCRUDDAO.create(entity, this); }

    @Getter private static final ThreadLocal<Boolean> rawMode = new ThreadLocal<>();

    public static <E extends Identifiable> E create(E entity, AbstractCRUDDAO<E> dao) {
        final boolean rawMode = isRawMode();
        final Object ctx;
        if (rawMode) {
            ctx = null;
        } else {
            entity.beforeCreate();
            ctx = dao.preCreate(entity);
        }
        setFlushMode(dao.getHibernateTemplate());
        entity.setUuid((String) dao.getHibernateTemplate().save(checkNotNull(entity)));
        try {
            dao.getHibernateTemplate().flush();
        } catch (RuntimeException e) {
            if (log.isDebugEnabled()) {
                log.error("create("+entity.getClass().getName()+"/"+json(entity)+"): "+e);
            } else {
                log.error("create: " + e);
            }
            throw e;
        }
        return rawMode ? entity : dao.postCreate(entity, ctx);
    }

    protected static boolean isRawMode() {
        return AbstractCRUDDAO.rawMode.get() != null && AbstractCRUDDAO.rawMode.get();
    }

    @Override public E createOrUpdate(@Valid E entity) {
        return (entity.getUuid() == null) ? create(entity) : update(entity);
    }
    public static <E extends Identifiable> E createOrUpdate(@Valid E entity, DAO<E> dao) {
        return (entity.getUuid() == null) ? dao.create(entity) : dao.update(entity);
    }

    public E upsert(@Valid E entity) {
        if (entity.getUuid() == null) throw new IllegalArgumentException("upsert: uuid must not be null");
        return exists(entity.getUuid()) ? update(entity) : create(entity);
    }

    @Override public Object preUpdate(@Valid E entity) {
        validateOrException(entity);
        try {
            return auditingEnabled() ? audit(findByUuid(entity.getUuid()), entity, CrudOperation.update) : entity;
        } finally {
            flushObjectCache(entity);
        }
    }

    @Override public E postUpdate(E entity, Object context) {
        return auditingEnabled() ? commit_audit(entity, context) : entity;
    }

    @Override public E update(@Valid E entity) { return update(entity, this); }

    public static <E extends Identifiable> E update(@Valid E entity, AbstractCRUDDAO<E> dao) {
        final boolean rawMode = isRawMode();
        final Object ctx;
        if (!rawMode) {
            entity.beforeUpdate();
            ctx = dao.preUpdate(entity);
        } else {
            ctx = null;
        }
        setFlushMode(dao.getHibernateTemplate());
        entity = dao.getHibernateTemplate().merge(checkNotNull(entity));
        dao.getHibernateTemplate().flush();
        return rawMode ? entity : dao.postUpdate(entity, ctx);
    }

    @Override public void delete(String uuid) {
        final E found = get(checkNotNull(uuid));
        setFlushMode();
        if (found != null) {
            final AuditLog auditLog = auditingEnabled() ? audit_delete(found) : null;
            getHibernateTemplate().delete(found);
            getHibernateTemplate().flush();
            flushObjectCache(found);
            if (auditLog != null) commit_audit_delete(auditLog);
        }
    }

    @Override public void delete(Collection<E> entities) {
        if (empty(entities)) return;
        setFlushMode();
        final List<AuditLog> logs = auditingEnabled() ? new ArrayList<>() : null;
        if (logs != null) {
            for (E e : entities) {
                logs.add(audit_delete(checkNotNull(e)));
            }
        }
        getHibernateTemplate().deleteAll(entities);
        getHibernateTemplate().flush();
        if (logs != null) {
            for (AuditLog log : logs) {
                commit_audit_delete(log);
            }
        }
    }

    @Transactional(readOnly=true)
    @Override public E findByUniqueField(String field, Object value) {
        return uniqueResult(value == null ? isNull(field) : eq(field, value));
    }

    @Transactional(readOnly=true)
    public E findByUniqueFields(String f1, Object v1, String f2, Object v2) {
        final Criterion expr1 = v1 == null ? isNull(f1) : eq(f1, v1);
        final Criterion expr2 = v2 == null ? isNull(f2) : eq(f2, v2);
        return uniqueResult(and(expr1, expr2));
    }

    @Transactional(readOnly=true)
    public E findByUniqueFields(String f1, Object v1, String f2, Object v2, String f3, Object v3) {
        final Criterion expr1 = v1 == null ? isNull(f1) : eq(f1, v1);
        final Criterion expr2 = v2 == null ? isNull(f2) : eq(f2, v2);
        final Criterion expr3 = v3 == null ? isNull(f3) : eq(f3, v3);
        return uniqueResult(and(expr1, expr2, expr3));
    }

    @Transactional(readOnly=true)
    public E findByUniqueFields(String f1, Object v1, String f2, Object v2, String f3, Object v3, String f4, Object v4) {
        final Criterion expr1 = v1 == null ? isNull(f1) : eq(f1, v1);
        final Criterion expr2 = v2 == null ? isNull(f2) : eq(f2, v2);
        final Criterion expr3 = v3 == null ? isNull(f3) : eq(f3, v3);
        final Criterion expr4 = v4 == null ? isNull(f4) : eq(f4, v4);
        return uniqueResult(and(expr1, expr2, expr3, expr4));
    }

    @Transactional(readOnly=true)
    public E findByUniqueFields(String f1, Object v1, String f2, Object v2, String f3, Object v3, String f4, Object v4, String f5, Object v5) {
        final Criterion expr1 = v1 == null ? isNull(f1) : eq(f1, v1);
        final Criterion expr2 = v2 == null ? isNull(f2) : eq(f2, v2);
        final Criterion expr3 = v3 == null ? isNull(f3) : eq(f3, v3);
        final Criterion expr4 = v4 == null ? isNull(f4) : eq(f4, v4);
        final Criterion expr5 = v5 == null ? isNull(f5) : eq(f5, v5);
        return uniqueResult(and(expr1, expr2, expr3, expr4, expr5));
    }

    @Transactional(readOnly=true)
    public E findByUniqueFields(String f1, Object v1, String f2, Object v2, String f3, Object v3, String f4, Object v4, String f5, Object v5, String f6, Object v6) {
        final Criterion expr1 = v1 == null ? isNull(f1) : eq(f1, v1);
        final Criterion expr2 = v2 == null ? isNull(f2) : eq(f2, v2);
        final Criterion expr3 = v3 == null ? isNull(f3) : eq(f3, v3);
        final Criterion expr4 = v4 == null ? isNull(f4) : eq(f4, v4);
        final Criterion expr5 = v5 == null ? isNull(f5) : eq(f5, v5);
        final Criterion expr6 = v6 == null ? isNull(f6) : eq(f6, v6);
        return uniqueResult(and(expr1, expr2, expr3, expr4, expr5, expr6));
    }

    @Transactional(readOnly=true)
    @Override public List<E> findByField(String field, Object value) {
        final Criterion c = value == null ? isNull(field) : eq(field, value);
        return list(sort(criteria().add(c)), 0, getFinderMaxResults());
    }

    protected DetachedCriteria sort(DetachedCriteria criteria) {
        final Order order = getDefaultSortOrder();
        return order == null ? criteria : criteria.addOrder(order);
    }

    public Order getDefaultSortOrder() { return null; }

    @Transactional(readOnly=true)
    @Override public List<E> findByFieldLike(String field, String value) {
        return list(criteria().add(ilike(field, value)).addOrder(Order.asc(field)), 0, getFinderMaxResults());
    }

    @Transactional(readOnly=true)
    public List<E> findByFieldLike(String field, String value, Order order) {
        return list(criteria().add(ilike(field, value)).addOrder(order), 0, getFinderMaxResults());
    }

    @Transactional(readOnly=true)
    @Override public List<E> findByFieldEqualAndFieldLike(String eqField, Object eqValue, String likeField, String likeValue) {
        return findByFieldEqualAndFieldLike(eqField, eqValue, likeField, likeValue, Order.asc(likeField));
    }

    @Transactional(readOnly=true)
    @Override public List<E> findByFieldEqualAndFieldLike(String eqField, Object eqValue, String likeField, String likeValue, Order order) {
        final Criterion expr1 = eqValue == null ? isNull(eqField) : eq(eqField, eqValue);
        return list(criteria().add(and(
                expr1,
                ilike(likeField, likeValue)
        )).addOrder(order), 0, getFinderMaxResults());
    }

    @Transactional(readOnly=true)
    public List<E> findByFieldLikeAndNewerThan(String likeField, String likeValue, Long mtime) {
        DetachedCriteria criteria = criteria().add(and(
                ilike(likeField, likeValue)
        ));
        if (mtime != null) criteria = criteria.add(gt("mtime", mtime));
        return list(criteria, 0, getFinderMaxResults());
    }

    @Transactional(readOnly=true)
    public List<E> findByFieldsEqualAndFieldLike(String f1, Object v1, String f2, Object v2, String likeField, String likeValue) {
        final Criterion expr1 = v1 == null ? isNull(f1) : eq(f1, v1);
        final Criterion expr2 = v2 == null ? isNull(f2) : eq(f2, v2);
        return list(criteria().add(and(
                expr1,
                expr2,
                ilike(likeField, likeValue)
        )).addOrder(Order.asc(likeField)), 0, getFinderMaxResults());
    }

    @Transactional(readOnly=true)
    public List<E> findByFieldsEqualAndFieldLike(String f1, Object v1, String f2, Object v2, String f3, Object v3, String likeField, String likeValue) {
        final Criterion expr1 = v1 == null ? isNull(f1) : eq(f1, v1);
        final Criterion expr2 = v2 == null ? isNull(f2) : eq(f2, v2);
        final Criterion expr3 = v3 == null ? isNull(f3) : eq(f3, v3);
        return list(criteria().add(and(
                expr1,
                expr2,
                expr3,
                ilike(likeField, likeValue)
        )).addOrder(Order.asc(likeField)), 0, getFinderMaxResults());
    }

    public DetachedCriteria buildFindInCriteria(String field, @NotNull Object[] values) {
        return criteria().add(in(field, values)).addOrder(Order.asc(field));
    }

    public DetachedCriteria buildFindInCriteria(String field, @NotNull Collection<?> values) {
        return criteria().add(in(field, values)).addOrder(Order.asc(field));
    }

    @Transactional(readOnly=true)
    @Override public List<E> findByFieldIn(String field, Object[] values) {
        return empty(values) ? new ArrayList<E>() : list(buildFindInCriteria(field, values), 0, getFinderMaxResults());
    }

    @Transactional(readOnly=true)
    @Override public List<E> findByFieldIn(String field, Collection<?> values) {
        return empty(values) ? new ArrayList<E>() : list(buildFindInCriteria(field, values), 0, getFinderMaxResults());
    }

    @SuppressWarnings("Duplicates")
    @Transactional(readOnly=true)
    @Override public List<E> findByFieldAndFieldIn(String field, Object value, String field2, Object[] values, Order order) {
        final DetachedCriteria criteria = criteria().add(and(value == null ? isNull(field) : eq(field, value), in(field2,


                values))).addOrder(order);
        return empty(values) ? new ArrayList<E>() : list(criteria, 0, getFinderMaxResults());
    }

    @SuppressWarnings("Duplicates")
    @Transactional(readOnly=true)
    @Override public List<E> findByFieldAndFieldIn(String field, Object value, String field2, Collection<?> values, Order order) {
        final DetachedCriteria criteria = criteria().add(and(value == null ? isNull(field) : eq(field, value), in(field2, values))).addOrder(order);
        return empty(values) ? new ArrayList<E>() : list(criteria, 0, getFinderMaxResults());
    }

    @Transactional(readOnly=true)
    public E findFirstByFieldIn(String field, Object[] values) {
        return empty(values) ? null : first(buildFindInCriteria(field, values));
    }

    @Transactional(readOnly=true)
    public E findFirstByFieldIn(String field, Collection<?> values) {
        return empty(values) ? null : first(buildFindInCriteria(field, values));
    }

    protected int getFinderMaxResults() { return isRawMode() ? Integer.MAX_VALUE : 100; }

    @Transactional(readOnly=true)
    public List<E> findByFields(String f1, Object v1, String f2, Object v2) {
        final Criterion expr1 = v1 == null ? isNull(f1) : eq(f1, v1);
        final Criterion expr2 = v2 == null ? isNull(f2) : eq(f2, v2);
        return list(sort(criteria().add(and(expr1, expr2))), 0, getFinderMaxResults());
    }

    @Transactional(readOnly=true)
    public List<E> findByFields(String f1, Object v1, String f2, Object v2, String f3, Object v3) {
        final Criterion expr1 = v1 == null ? isNull(f1) : eq(f1, v1);
        final Criterion expr2 = v2 == null ? isNull(f2) : eq(f2, v2);
        final Criterion expr3 = v3 == null ? isNull(f3) : eq(f3, v3);
        return list(sort(criteria().add(and(expr1, expr2, expr3))), 0, getFinderMaxResults());
    }

    @Transactional(readOnly=true)
    public List<E> findByFields(String f1, Object v1, String f2, Object v2, String f3, Object v3, String f4, Object v4) {
        final Criterion expr1 = v1 == null ? isNull(f1) : eq(f1, v1);
        final Criterion expr2 = v2 == null ? isNull(f2) : eq(f2, v2);
        final Criterion expr3 = v3 == null ? isNull(f3) : eq(f3, v3);
        final Criterion expr4 = v4 == null ? isNull(f4) : eq(f4, v4);
        return list(sort(criteria().add(and(expr1, expr2, expr3, expr4))), 0, getFinderMaxResults());
    }

    @Transactional(readOnly=true)
    public List<E> findByFields(String f1, Object v1, String f2, Object v2, String f3, Object v3, String f4, Object v4, String f5, Object v5) {
        final Criterion expr1 = v1 == null ? isNull(f1) : eq(f1, v1);
        final Criterion expr2 = v2 == null ? isNull(f2) : eq(f2, v2);
        final Criterion expr3 = v3 == null ? isNull(f3) : eq(f3, v3);
        final Criterion expr4 = v4 == null ? isNull(f4) : eq(f4, v4);
        final Criterion expr5 = v5 == null ? isNull(f5) : eq(f5, v5);
        return list(sort(criteria().add(and(expr1, expr2, expr3, expr4, expr5))), 0, getFinderMaxResults());
    }

    @Transactional(readOnly=true)
    public List<E> findByFields(String f1, Object v1, String f2, Object v2, String f3, Object v3, String f4, Object v4, String f5, Object v5, String f6, Object v6) {
        final Criterion expr1 = v1 == null ? isNull(f1) : eq(f1, v1);
        final Criterion expr2 = v2 == null ? isNull(f2) : eq(f2, v2);
        final Criterion expr3 = v3 == null ? isNull(f3) : eq(f3, v3);
        final Criterion expr4 = v4 == null ? isNull(f4) : eq(f4, v4);
        final Criterion expr5 = v5 == null ? isNull(f5) : eq(f5, v5);
        final Criterion expr6 = v6 == null ? isNull(f6) : eq(f6, v6);
        return list(sort(criteria().add(and(expr1, expr2, expr3, expr4, expr5, expr6))), 0, getFinderMaxResults());
    }

    @Getter private final AtomicReference<Map<String, Object>> ocache = new AtomicReference<>(new ConcurrentHashMap<>());

    private static final Object NULL_OBJECT = new Object();
    private static final AtomicInteger cacheHits = new AtomicInteger(0);
    private static final AtomicInteger cacheMisses = new AtomicInteger(0);
    private static final AtomicLong cacheMissTime = new AtomicLong(0);

    @Transactional(readOnly=true)
    public <T> T cacheLookup(String cacheKey, Function<Object[], T> lookup, Object... args) {
        return cacheLookup(cacheKey, NO_SUB_KEY, lookup, args);
    }

    @Transactional(readOnly=true)
    public <T> T cacheLookup(String cacheKey, String cacheSubKey, Function<Object[], T> lookup, Object... args) {
        final String subCacheAttr = subCacheAttribute();
        final Map<String, Object> c;
        synchronized (ocache) {
            c = subCacheAttr == null ? ocache.get() : (Map<String, Object>) ocache.get().computeIfAbsent(cacheSubKey, o -> new ConcurrentHashMap<String, Object>());
        }
        if (!c.containsKey(cacheKey)) {
            synchronized (c) {
                if (!c.containsKey(cacheKey)) {
                    final long start = now();
                    final T thing;
                    try {
                        thing = lookup.apply(args);
                    } catch (Exception e) {
                        return die("cacheLookup: lookup failed: "+e, e);
                    }
                    final long end = now();
                    int misses = cacheMisses.incrementAndGet();
                    long missTime = cacheMissTime.addAndGet(end - start);
                    if (misses % 1000 == 0) log.info("DAO-cache: "+misses+" misses took "+cacheMissTime + " to look up, average of "+(missTime/misses)+"ms per lookup");

                    c.put(cacheKey, thing == null ? NULL_OBJECT : thing);
                }
            }
        }
        return getOrNull(cacheKey, c);
    }

    private <T> T getOrNull(String cacheKey, Map<String, Object> c) {
        int hits = cacheHits.incrementAndGet();
        if (hits % 1000 == 0) log.info("DAO-cache: "+hits+" cache hits, saved "+formatDuration(hits*(cacheMissTime.get()/cacheMisses.get())));
        return cacheCopy((T) c.get(cacheKey));
    }

    private <T> T cacheCopy(T thing) {
        if (thing == NULL_OBJECT) return null;
        if (empty(thing)) return thing;
        try {
            if (thing instanceof Collection) {
                final Collection c = (Collection) instantiate(thing.getClass());
                for (Iterator iter = ((Collection) thing).iterator(); iter.hasNext(); ) {
                    final Object element = iter.next();
                    c.add(cacheCopy(element));
                }
                return (T) c;
            } else {
                return mirror(thing);
            }
        } catch (Exception e) {
            return die("cacheCopy: error copying: " + thing + ": " + e, e);
        }
    }

    @Transactional(readOnly=true)
    public E cacheLookup(String uuid, Map<String, E> cache) {
        final E thing = cache.get(uuid);
        return (thing != null) ? thing : findByUuid(uuid);
    }

    protected void setFlushMode() { setFlushMode(getHibernateTemplate()); }
    public static void setFlushMode(HibernateTemplate template) { template.getSessionFactory().getCurrentSession().setFlushMode(FlushMode.COMMIT); }

    public void refresh(E entity) { getHibernateTemplate().getSessionFactory().getCurrentSession().refresh(entity); }

    private static final String PROP_AUDIT_LOG = "__auditLog";

    private Object audit(E prevEntity, E newEntity, CrudOperation operation) {

        if (newEntity == null) die("audit("+operation.name()+"): newEntity cannot be null");

        AuditLog auditLog = getAuditLogDAO().newEntity()
                .setEntityType(getEntityClass().getName())
                .setEntityUuid(newEntity.getUuid())
                .setOperation(operation)
                .setPrevState(prevEntity == null ? null : toJsonOrDie(toMap(prevEntity)))
                .setNewState(toJsonOrDie(toMap(newEntity, getAuditFields(), getAuditExcludeFields())));

        auditLog = getAuditLogDAO().create(auditLog);

        final Map<String, Object> ctx = new HashMap<>();
        ctx.put(PROP_AUDIT_LOG, auditLog);
        return ctx;
    }

    protected String[] getAuditFields() { return null; }
    protected String[] getAuditExcludeFields() { return null; }

    private E commit_audit(E entity, Object context) {
        final Map<String, Object> ctx = (Map<String, Object>) context;
        final AuditLog auditLog = (AuditLog) ctx.get(PROP_AUDIT_LOG);
        auditLog.setSuccess(true);
        getAuditLogDAO().update(auditLog);
        return entity;
    }

    private AuditLog audit_delete(E found) {
        AuditLog auditLog = getAuditLogDAO().newEntity()
                .setEntityType(getEntityClass().getName())
                .setEntityUuid(found.getUuid())
                .setOperation(CrudOperation.delete)
                .setPrevState(toJsonOrDie(toMap(found)))
                .setNewState(null);

        return getAuditLogDAO().create(auditLog);
    }

    private void commit_audit_delete(AuditLog auditLog) {
        auditLog.setSuccess(true);
        getAuditLogDAO().update(auditLog);
    }

}
