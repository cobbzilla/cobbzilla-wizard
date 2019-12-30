package org.cobbzilla.wizard.dao;

/**
 * Forked from dropwizard https://github.com/dropwizard/
 * https://github.com/dropwizard/dropwizard/blob/master/LICENSE
 */

import lombok.Getter;
import lombok.Setter;
import org.cobbzilla.util.collection.NameAndValue;
import org.cobbzilla.util.reflect.ReflectionUtil;
import org.cobbzilla.util.string.StringUtil;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.IdentifiableBase;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECSearchable;
import org.cobbzilla.wizard.model.search.SearchQuery;
import org.cobbzilla.wizard.model.search.SqlViewField;
import org.cobbzilla.wizard.model.search.SqlViewFieldSetter;
import org.cobbzilla.wizard.server.config.PgRestServerConfiguration;
import org.hibernate.Criteria;
import org.hibernate.Hibernate;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.DetachedCriteria;
import org.jasypt.hibernate4.encryptor.HibernatePBEStringEncryptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.hibernate4.HibernateTemplate;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.reflect.FieldUtils.getAllFields;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.reflect.ReflectionUtil.instantiate;
import static org.cobbzilla.util.string.StringUtil.camelCaseToSnakeCase;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.isEncryptedField;

/**
 * An abstract base class for Hibernate DAO classes.
 *
 * @param <E> the class which this DAO manages
 */
public abstract class AbstractDAO<E extends Identifiable> implements DAO<E> {

    @Autowired @Getter @Setter private HibernateTemplate hibernateTemplate;
    @Autowired @Getter @Setter private HibernatePBEStringEncryptor hibernateEncryptor;
    @Autowired @Getter @Setter private PgRestServerConfiguration configuration;

    private final Class<?> entityClass;

    /**
     * Creates a new DAO with a given session provider.
     */
    public AbstractDAO() { this.entityClass = ReflectionUtil.getTypeParameter(getClass()); }

    /**
     * Creates a new {@link Criteria} query for {@code <E>}.
     *
     * @return a new {@link Criteria} query
     * @see Session#createCriteria(Class)
     */
    protected DetachedCriteria criteria() { return DetachedCriteria.forClass(getEntityClass()); }

    protected DetachedCriteria criteria(Class entityClass) { return DetachedCriteria.forClass(entityClass); }

    /**
     * Returns the entity class managed by this DAO.
     *
     * @return the entity class managed by this DAO
     */
    @SuppressWarnings("unchecked")
    public Class<E> getEntityClass() { return (Class<E>) entityClass; }

    /**
     * Creates a new instance of the entity class using the default constructor
     * @return a new instance of E
     */
    public E newEntity () { return (E) instantiate(entityClass); }

    /**
     * Creates a new instance of the entity class using the copy constructor
     * @return a new instance of E created with the copy constructor
     */
    public E newEntity (E other) { return (E) instantiate(entityClass, other); }

    /**
     * Returns a single canonical instance of the entity class. DO NOT MODIFY THE OBJECT RETURNED.
     */
    @Getter(lazy=true) private final E entityProto = initEntityProto();
    private E initEntityProto() { return newEntity(); }

    /**
     * Convenience method to return a single instance that matches the criteria, or null if the
     * criteria returns no results.
     *
     * @param criteria the {@link Criteria} query to run
     * @return the single result or {@code null}
     * @throws HibernateException if there is more than one matching result
     * @see Criteria#uniqueResult()
     */
    @SuppressWarnings("unchecked")
    protected E uniqueResult(DetachedCriteria criteria) throws HibernateException {
        return (E) DAOUtil.uniqueResult(getHibernateTemplate().findByCriteria(criteria));
    }

    protected E uniqueResult(Criterion expression) {
        return uniqueResult(criteria().add(expression));
    }

    /**
     * Get the results of a {@link Criteria} query.
     *
     * @param criteria the {@link Criteria} query to run
     * @return the list of matched query results
     * @see Criteria#list()
     */
    @SuppressWarnings("unchecked")
    protected List<E> list(DetachedCriteria criteria) throws HibernateException {
        return (List<E>) getHibernateTemplate().findByCriteria(checkNotNull(criteria));
    }

    /**
     * Get the results of a {@link Criteria} query, with a firstResult and maxResults
     *
     * @param criteria the {@link Criteria} query to run
     * @param firstResult the first result number (skip results before this)
     * @param maxResults the maximum number of results
     * @return the list of matched query results
     * @see Criteria#list()
     */
    @SuppressWarnings("unchecked")
    protected List<E> list(DetachedCriteria criteria, int firstResult, int maxResults) throws HibernateException {
        return (List<E>) getHibernateTemplate().findByCriteria(checkNotNull(criteria), firstResult, maxResults);
    }

    /**
     * Apply a filter and continue querying the database until maxResults or end of query results
     *
     * @param criteria the {@link Criteria} query to run
     * @param firstResult the first result number (skip results before this)
     * @param maxResults the maximum number of results
     * @param filter An object implementing the EntityFilter interface
     * @return the list of matched query results
     * @see Criteria#list()
     */
    @SuppressWarnings("unchecked")
    protected List<E> list(DetachedCriteria criteria, int firstResult, int maxResults, EntityFilter<E> filter) throws HibernateException {
        final List<E> results = new ArrayList<>();
        int offset = firstResult;
        while (results.size() < maxResults) {

            final List<E> candidates = (List<E>) getHibernateTemplate().findByCriteria(checkNotNull(criteria), offset, maxResults);
            offset += candidates.size();
            if (candidates.isEmpty()) {
                if (offset == firstResult) return null; // end of everything
            } else {
                break;
            }
            if (filter == null) return candidates;

            for (E thing : candidates) {
                if (filter.isAcceptable(thing)) results.add(thing);
            }
        }
        return results;
    }

    /**
     * Get the first results of a {@link Criteria} query.
     * @param criteria the {@link Criteria} query to run
     * @return the first query result, or null if no results
     */
    @SuppressWarnings("unchecked")
    protected E first(DetachedCriteria criteria) throws HibernateException {
        final List<E> found = (List<E>) getHibernateTemplate().findByCriteria(checkNotNull(criteria), 0, 1);
        return found.isEmpty() ? null : found.get(0);
    }

    public List query(String hsql, String[] paramNames, Object[] paramValues, int maxResults) {
        final HibernateCallbackImpl callback = new HibernateCallbackImpl(hsql, paramNames, paramValues, 0, maxResults);
        return (List) getHibernateTemplate().execute(callback);
    }

    public Session readOnlySession() {
        final Session session = getHibernateTemplate().getSessionFactory().openSession();
        session.setDefaultReadOnly(true);
        return session;
    }

    /**
     * Return the persistent instance of {@code <E>} with the given identifier, or {@code null} if
     * there is no such persistent instance. (If the instance, or a proxy for the instance, is
     * already associated with the session, return that instance or proxy.)
     *
     * @param id an identifier
     * @return a persistent instance or {@code null}
     * @throws HibernateException
     * @see Session#get(Class, Serializable)
     */
    @SuppressWarnings("unchecked")
    @Override public E get(Serializable id) { return (E) getHibernateTemplate().get(entityClass, checkNotNull(id)); }

    /**
     * Either save or update the given instance, depending upon resolution of the unsaved-value
     * checks (see the manual for discussion of unsaved-value checking).
     * <p/>
     * This operation cascades to associated instances if the association is mapped with
     * <tt>cascade="save-update"</tt>.
     *
     * @param entity a transient or detached instance containing new or updated state
     * @throws HibernateException
     * @see Session#saveOrUpdate(Object)
     */
//    @Transactional
    public E persist(E entity) throws HibernateException {
        getHibernateTemplate().saveOrUpdate(checkNotNull(entity));
        return entity;
    }

    /**
     * Force initialization of a proxy or persistent collection.
     * <p/>
     * Note: This only ensures initialization of a proxy object or collection;
     * it is not guaranteed that the elements INSIDE the collection will be initialized/materialized.
     *
     * @param proxy a persistable object, proxy, persistent collection or {@code null}
     * @throws HibernateException if we can't initialize the proxy at this time, eg. the {@link Session} was closed
     */
    protected <T> T initialize(T proxy) throws HibernateException {
        if (!Hibernate.isInitialized(proxy)) {
            Hibernate.initialize(proxy);
        }
        return proxy;
    }

    public void delete(Collection<E> entities) {
        for (E entity : entities) delete(entity.getUuid());
    }

    public static final String entityAlias = "x";
    public static final String FILTER_PARAM = "filter";
    public static final String[] EMPTY_PARAMS = new String[0];
    public static final Object[] EMPTY_VALUES = new Object[0];
    public static final String[] PARAM_FILTER = new String[]{FILTER_PARAM};

    @Getter(lazy=true) private final SqlViewField[] searchFields = initSearchFields();

    private SqlViewField[] initSearchFields() {
        final Map<String, SqlViewField> fields = new LinkedHashMap<>();
        final Class<E> entityClass = getEntityClass();
        Class c = entityClass;
        while (!c.equals(Object.class)) {
            for (Field f : getAllFields(entityClass)) {
                final ECSearchable search = f.getAnnotation(ECSearchable.class);
                if (search == null || fields.containsKey(f.getName())) continue;

                final String property = empty(search.property()) ? f.getName() : search.property();

                final SqlViewFieldSetter set = search.setter().equals(ECSearchable.DefaultSqlViewFieldSetter.class)
                        ? null : instantiate(search.setter());

                String entity = empty(search.entity()) ? entityClass.getName() : search.entity();
                fields.putIfAbsent(f.getName(), new SqlViewField(camelCaseToSnakeCase(f.getName()))
                        .setType(entityClass)
                        .fieldType(f.getType())
                        .encrypted(isEncryptedField(f))
                        .filter(search.filter())
                        .property(property)
                        .entity(entity)
                        .setter(set));
            }
            c = c.getSuperclass();
        }
        return fields.values().toArray(new SqlViewField[0]);
    }

    @Override public SearchResults<E> search(SearchQuery searchQuery) {
        return search(searchQuery, getEntityClass().getSimpleName());
    }

    @Override public SearchResults<E> search(SearchQuery searchQuery, String entityType) {
        if (this instanceof SqlViewSearchableDAO) {
            final SqlViewSearchableDAO sqlSearch = (SqlViewSearchableDAO) this;
            return SqlViewSearchHelper.search(sqlSearch, searchQuery, sqlSearch.getResultClass(), getSearchFields(), hibernateEncryptor, configuration);
        }
        final StringBuilder filterClause;
        String[] params;
        Object[] values;
        if (searchQuery.getHasFilter()) {
            params = PARAM_FILTER;
            values = new Object[] { StringUtil.sqlFilter(searchQuery.getFilter()) };
            filterClause = new StringBuilder(getFilterClause(entityAlias, FILTER_PARAM));
        } else {
            params = EMPTY_PARAMS;
            values = EMPTY_VALUES;
            filterClause = new StringBuilder();
        }
        if (searchQuery.getHasBounds()) {
            for (NameAndValue bound : searchQuery.getBounds()) {
                if (filterClause.length() > 0) filterClause.append("and ");
                filterClause.append(formatBound(entityAlias, bound.getName(), bound.getValue()));
            }
        }
        if (filterClause.length() > 0) filterClause.insert(0, "where ");

        final String selectClause = getSelectClause(searchQuery);
        final StringBuilder qBuilder = new StringBuilder("select ")
                .append(selectClause).append(" ")
                .append("from ").append(getEntityClass().getSimpleName()).append(" ").append(entityAlias).append(" ")
                .append(filterClause);

        final String countQuery = "select count(*) " + qBuilder.toString();
        final String query = qBuilder.append(" order by ").append(entityAlias).append(".").append(searchQuery.getSortField()).append(" ").append(searchQuery.getSortType().name()).toString();

        List<E> results = query(query, searchQuery, params, values);
        final int totalCount = Integer.valueOf(""+query(countQuery, SearchQuery.INFINITE_PAGE, params, values).get(0));

        // the caller may want the results filtered (remove sensitive fields)
        if (searchQuery.hasScrubber() && !results.isEmpty()) {
            results = searchQuery.getScrubber().scrub(results);
        }

        return new SearchResults<>(results, totalCount);
    }

    // default search view is the table itself. subclasses can override this and provide custom views
    @Getter(lazy=true) private final String searchView = camelCaseToSnakeCase(getEntityClass().getSimpleName().replace(".", ""));

    public String getSelectClause(SearchQuery searchQuery) {
        final SqlViewField[] searchFields = getSearchFields();

        final StringBuilder selectFields = new StringBuilder();
        if (!searchQuery.getHasFields()) {
            if (empty(searchFields)) return "*";
            for (SqlViewField field : searchFields) {
                if (selectFields.length() > 0) selectFields.append(", ");
                selectFields.append(field.getName());
            }
            return selectFields.toString();
        }

        if (empty(searchFields)) die("getSelectClause: requested specific fields but "+getClass().getSimpleName()+" returned null/empty from getSearchFields()");

        for (String field : searchQuery.getFields()) {
            final SqlViewField sqlViewField = Arrays.stream(searchFields)
                    .filter(f -> f.getName().equalsIgnoreCase(field))
                    .findFirst().orElse(null);
            if (sqlViewField == null) {
                return die("getSelectClause: cannot search for field "+field+", add @ECSearchable annotation to enable searching");
            }
            if (selectFields.length() > 0) selectFields.append(", ");
            selectFields.append(sqlViewField.getProperty());
        }
        return selectFields.toString();
    }

    public List query(String queryString, SearchQuery searchQuery, String[] params, Object[] values) {
        return query(queryString, searchQuery, params, values, null);
    }

    public List query(String queryString, SearchQuery searchQuery, String[] params, Object[] values, String[] listParams) {
        final HibernateCallbackImpl callback = new HibernateCallbackImpl(queryString, params, values, searchQuery.getPageOffset(), searchQuery.getPageSize());
        if (!empty(listParams)) {
            for (String listParam : listParams) {
                callback.markAsListParameter(listParam);
            }
        }
        return (List) getHibernateTemplate().execute(callback);
    }

    protected String formatBound(String entityAlias, String bound, String value) { return notSupported("Invalid bound: " + bound); }

    public static String caseInsensitiveLike(String entityAlias, String filterParam, final String attribute) {
        return new StringBuilder().append("lower(").append(entityAlias).append(".").append(attribute).append(") LIKE lower(:").append(filterParam).append(") ").toString();
    }

    protected String getFilterClause(String entityAlias, String filterParam) { return StringUtil.EMPTY; }

    public static String[] toUuidArray(List<? extends Identifiable> entities) { return IdentifiableBase.toUuidArray(entities); }
    public static List<String> toUuidList(List<? extends Identifiable> entities) { return IdentifiableBase.toUuidList(entities); }
    public static <T> T[] collectArray(List<? extends Identifiable> entities, String field) { return IdentifiableBase.collectArray(entities, field); }
    public static <T> List<T> collectList(List<? extends Identifiable> entities, String field) { return IdentifiableBase.collectList(entities, field); }
}
