package org.cobbzilla.wizard.dao.sql;

import com.google.common.base.CaseFormat;
import com.google.common.base.Converter;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.reflect.ReflectionUtil;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.server.config.DatabaseConfiguration;
import org.hibernate.engine.spi.RowSelection;
import org.hibernate.graph.GraphSemantic;
import org.hibernate.graph.RootGraph;
import org.hibernate.query.ParameterMetadata;
import org.hibernate.query.Query;
import org.hibernate.*;
import org.hibernate.query.QueryParameter;
import org.hibernate.query.QueryProducer;
import org.hibernate.transform.ResultTransformer;
import org.hibernate.type.ObjectType;
import org.hibernate.type.Type;

import javax.persistence.FlushModeType;
import javax.persistence.LockModeType;
import javax.persistence.Parameter;
import javax.persistence.TemporalType;
import java.io.Closeable;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.notSupported;
import static org.cobbzilla.util.reflect.ReflectionUtil.instantiate;

@Slf4j @Accessors(chain=true)
public class ObjectSQLQuery<E extends Identifiable> implements Query<E>, Closeable {

    public static final Converter<String, String> FIELD_NAME_CONVERTER = CaseFormat.LOWER_UNDERSCORE.converterTo(CaseFormat.LOWER_CAMEL);
    private DatabaseConfiguration database;
    private String sql;
    private Class<E> entityClass;

    private static final Map<String, Map<String, SQLFieldTransformer>> exemplars = new ConcurrentHashMap<>();

    protected Map<String, SQLFieldTransformer> getTransformers () {
        final String name = entityClass.getName();
        if (!exemplars.containsKey(name)) {
            synchronized (exemplars) {
                if (!exemplars.containsKey(name)) {
                    try {
                        exemplars.put(name, ((SQLMappable) instantiate(entityClass)).getSQLFieldTransformers());
                    } catch (Exception e) {
                        log.warn("getSQLFieldTransformers: error with " + name + ": " + e);
                        exemplars.put(name, new HashMap<String, SQLFieldTransformer>());
                    }
                }
            }
        }
        return exemplars.get(name);
    }

    private Integer maxResults;
    private Integer firstResult;
    @Getter private Integer fetchSize;
    @Getter private Integer timeout;
    @Getter private boolean readOnly = true;

    @Override public int getMaxResults () { return maxResults; }
    @Override public int getFirstResult () { return firstResult; }

    private Connection connection = null;
    private PreparedStatement statement = null;
    private ResultSet resultSet = null;

    public ObjectSQLQuery(DatabaseConfiguration database, String sql, Class<E> entityClass) {
        this.database = database;
        this.sql = sql;
        this.entityClass = entityClass;
    }

    @Override public void close() {
        try { ReflectionUtil.close(resultSet);  } catch (Exception e) { log.warn("error closing ResultSet: "+e); }
        try { ReflectionUtil.close(statement);  } catch (Exception e) { log.warn("error closing PreparedStatement: "+e); }
        try { ReflectionUtil.close(connection); } catch (Exception e) { log.warn("error closing Connection: "+e); }
    }

    @Override public String getQueryString() { return sql; }
    @Override public RowSelection getQueryOptions () { return notSupported(); }
    @Override public ParameterMetadata getParameterMetadata () { return notSupported(); }

    @Override public Query<E> setMaxResults (int maxResults)   { this.maxResults = maxResults; return this; }
    @Override public Query<E> setFirstResult(int firstResult)  { this.firstResult = firstResult; return this; }
    @Override public Query<E> setTimeout    (int timeout)      { this.timeout = timeout; return this; }
    @Override public Query<E> setFetchSize  (int fetchSize)    { this.fetchSize = fetchSize; return this; }
    @Override public Query<E> setReadOnly   (boolean readOnly) { this.readOnly = readOnly; return this; }

    @Override public FlushModeType getFlushMode() { return FlushModeType.AUTO; }
    @Override public Query<E> setFlushMode (FlushModeType flushModeType) { return this; }

    @Override public Query<E> setHibernateFlushMode (FlushMode flushMode) { return notSupported(); }

    @Override public CacheMode getCacheMode() { return CacheMode.IGNORE; }
    @Override public Query<E> setCacheMode(CacheMode cacheMode) { return this; }

    @Override public boolean isCacheable() { return false; }

    @Override public Query<E> setCacheable(boolean cacheable) { return this; }
    @Override public String getCacheRegion() { return null; }
    @Override public Query<E> setCacheRegion(String cacheRegion) { return this; }

    @Override public Type[] getReturnTypes() { return new Type[] {ObjectType.INSTANCE}; }

    @Override public LockOptions getLockOptions() { return LockOptions.NONE; }
    @Override public Query<E> setLockOptions(LockOptions lockOptions) { return this; }

    @Override public LockModeType getLockMode () { return null; }
    @Override public Query<E> setLockMode(String alias, LockMode lockMode) { return this; }
    @Override public Query<E> setLockMode (LockModeType lockModeType) { return this; }

    @Override public String getComment() { return null; }
    @Override public Query<E> setComment(String comment) { return this; }

    @Override public Map<String, Object> getHints () { return notSupported(); }
    @Override public Query<E> addQueryHint(String hint) { return null; }
    @Override public Query<E> setHint (String s, Object o) { return notSupported(); }

    @Override public <P> Query<E> setParameterList (QueryParameter<P> queryParameter, Collection<P> collection) { return notSupported(); }

    @Override public String[] getReturnAliases() { return new String[] {entityClass.getSimpleName()}; }

    @Override public String[] getNamedParameters() { return new String[0]; }

    @Override public Iterator<E> iterate() { return notSupported(); }

    @Override public QueryProducer getProducer () { return notSupported(); }
    @Override public Stream<E> stream () { return notSupported(); }
    @Override public Query<E> applyGraph (RootGraph rootGraph, GraphSemantic graphSemantic) { return notSupported(); }

    @Override public ScrollableResults scroll() { return notSupported(); }
    @Override public ScrollableResults scroll(ScrollMode scrollMode) { return notSupported(); }

    @Override public List<E> list() {
        try {
            initStatement();
            resultSet = statement.executeQuery();

            final List<E> results = new ArrayList<>();
            final ResultSetMetaData metaData = resultSet.getMetaData();
            while (resultSet.next()) {
                results.add(rowToObject(resultSet, metaData));
            }
            return results;

        } catch (Exception e) {
            return die("list: "+e, e);
        }
    }

    private void initStatement() throws SQLException {
        if (connection == null) connection = database.getConnection();
        if (statement == null) {
            if (firstResult != null) sql += " OFFSET " + firstResult;
            if (maxResults != null) sql += " LIMIT " + maxResults;
            statement = connection.prepareStatement(sql);
        }
    }

    private E rowToObject(ResultSet resultSet, ResultSetMetaData metaData) throws Exception {
        final E object = instantiate(entityClass);

        for (int i=1; i<=metaData.getColumnCount(); i++) {
            final Object columnVal = resultSet.getObject(i);
            if (columnVal == null) continue;

            final String columnName = metaData.getColumnName(i);
            final SQLFieldTransformer transformer = getTransformers().get(columnName);

            if (transformer != null) {
                final Object value = transformer.sqlToObject(object, columnVal);
                if (value != null) ReflectionUtil.set(object, columnName, value);
            } else {
                final String fieldName = FIELD_NAME_CONVERTER.convert(columnName);
                ReflectionUtil.set(object, fieldName, columnVal);
            }
        }
        return object;
    }

    @Override public E uniqueResult() {
        setMaxResults(1);
        final List<E> found = list();
        return found.isEmpty() ? null : found.get(0);
    }
    @Override public Optional<E> uniqueResultOptional () { return Optional.of(uniqueResult()); }

    @Override public FlushMode getHibernateFlushMode () { return FlushMode.AUTO; }

    @Override public int executeUpdate() { return notSupported(); }

    @Override public <T> T unwrap (Class<T> aClass) { return notSupported(); }

    @Override public boolean isBound (Parameter<?> parameter) { return notSupported(); }

    @Override public Query<E> setParameter (Parameter<Instant> parameter, Instant instant, TemporalType temporalType) { return notSupported(); }
    @Override public Query<E> setParameter (Parameter<LocalDateTime> parameter, LocalDateTime localDateTime, TemporalType temporalType) { return notSupported(); }
    @Override public Query<E> setParameter (Parameter<ZonedDateTime> parameter, ZonedDateTime zonedDateTime, TemporalType temporalType) { return notSupported(); }
    @Override public Query<E> setParameter (Parameter<OffsetDateTime> parameter, OffsetDateTime offsetDateTime, TemporalType temporalType) { return notSupported(); }
    @Override public Query<E> setParameter (String s, Instant instant, TemporalType temporalType) { return notSupported(); }
    @Override public Query<E> setParameter (String s, LocalDateTime localDateTime, TemporalType temporalType) { return notSupported(); }
    @Override public Query<E> setParameter (String s, ZonedDateTime zonedDateTime, TemporalType temporalType) { return notSupported(); }
    @Override public Query<E> setParameter (String s, OffsetDateTime offsetDateTime, TemporalType temporalType) { return notSupported(); }
    @Override public Query<E> setParameter (int i, Instant instant, TemporalType temporalType) { return notSupported(); }
    @Override public Query<E> setParameter (int i, LocalDateTime localDateTime, TemporalType temporalType) { return notSupported(); }
    @Override public Query<E> setParameter (int i, ZonedDateTime zonedDateTime, TemporalType temporalType) { return notSupported(); }
    @Override public Query<E> setParameter (int i, OffsetDateTime offsetDateTime, TemporalType temporalType) { return notSupported(); }
    @Override public Query<E> setParameter (int position, Object val, Type type) { return notSupported(); }
    @Override public Query<E> setParameter (String name, Object val) { return notSupported(); }
    @Override public Query<E> setParameter (String name, Object val, Type type) { return notSupported(); }
    @Override public Query<E> setParameter (String s, Calendar calendar, TemporalType temporalType) { return notSupported(); }
    @Override public Query<E> setParameter (String s, Date date, TemporalType temporalType) { return notSupported(); }
    @Override public Query<E> setParameter (int position, Object val) { return notSupported(); }
    @Override public Query<E> setParameter (int i, Calendar calendar, TemporalType temporalType) { return notSupported(); }
    @Override public Query<E> setParameter (int i, Date date, TemporalType temporalType) { return notSupported(); }
    @Override public Query<E> setParameter (Parameter<Calendar> parameter, Calendar calendar, TemporalType temporalType) { return notSupported(); }
    @Override public Query<E> setParameter (Parameter<Date> parameter, Date date, TemporalType temporalType) { return notSupported(); }
    @Override public <P> Query<E> setParameter (int i, P p, TemporalType temporalType) { return notSupported(); }
    @Override public <P> Query<E> setParameter (QueryParameter<P> queryParameter, P p, Type type) { return notSupported(); }
    @Override public <P> Query<E> setParameter (QueryParameter<P> queryParameter, P p, TemporalType temporalType) { return notSupported(); }
    @Override public <P> Query<E> setParameter (String s, P p, TemporalType temporalType) { return notSupported(); }
    @Override public <T> Query<E> setParameter (QueryParameter<T> queryParameter, T t) { return notSupported(); }
    @Override public <T> Query<E> setParameter (Parameter<T> parameter, T t) { return notSupported(); }

    @Override public Parameter<?> getParameter (String s) { return notSupported(); }
    @Override public Parameter<?> getParameter (int i) { return notSupported(); }
    @Override public Set<Parameter<?>> getParameters () { return notSupported(); }
    @Override public <T> Parameter<T> getParameter (String s, Class<T> aClass) { return notSupported(); }
    @Override public <T> Parameter<T> getParameter (int i, Class<T> aClass) { return notSupported(); }

    @Override public <T> T getParameterValue (Parameter<T> parameter) { return notSupported(); }
    @Override public Object getParameterValue (String s) { return notSupported(); }
    @Override public Object getParameterValue (int i) { return notSupported(); }

    @Override public Query<E> setParameters (Object[] values, Type[] types) { return notSupported(); }

    @Override public Query<E> setParameterList (String name, Collection values, Type type) { return notSupported(); }
    @Override public Query<E> setParameterList (String name, Collection values) { return notSupported(); }
    @Override public Query<E> setParameterList (String name, Object[] values, Type type) { return notSupported(); }
    @Override public Query<E> setParameterList (String name, Object[] values) { return notSupported(); }

    @Override public org.hibernate.Query<E> setParameterList (int i, Collection collection, Type type) { return notSupported(); }
    @Override public org.hibernate.Query<E> setParameterList (int i, Collection collection) { return notSupported(); }
    @Override public org.hibernate.Query<E> setParameterList (int i, Object[] objects, Type type) { return notSupported(); }
    @Override public org.hibernate.Query<E> setParameterList (int i, Object[] objects) { return notSupported(); }

    @Override public Query<E> setProperties (Object bean) { return notSupported(); }
    @Override public Query<E> setProperties (Map bean) { return notSupported(); }

    @Override public Query<E> setString(int position, String val) {
        try {
            initStatement();
            statement.setString(position, val);
        } catch (Exception e) { die("setString: "+e, e); }
        return this;
    }

    @Override public Query<E> setCharacter(int position, char val) {
        try {
            initStatement();
            statement.setString(position, ""+val);
        } catch (Exception e) { die("setCharacter: "+e, e); }
        return this;
    }

    @Override public Query<E> setBoolean(int position, boolean val) {
        try {
            initStatement();
            statement.setBoolean(position, val);
        } catch (Exception e) { die("setBoolean: "+e, e); }
        return this;
    }

    @Override public Query<E> setByte(int position, byte val) {
        try {
            initStatement();
            statement.setByte(position, val);
        } catch (Exception e) { die("setByte: "+e, e); }
        return this;
    }

    @Override public Query<E> setShort(int position, short val) {
        try {
            initStatement();
            statement.setShort(position, val);
        } catch (Exception e) { die("setShort: "+e, e); }
        return this;
    }

    @Override public Query<E> setInteger(int position, int val) {
        try {
            initStatement();
            statement.setInt(position, val);
        } catch (Exception e) { die("setInteger: "+e, e); }
        return this;
    }

    @Override public Query<E> setLong(int position, long val) {
        try {
            initStatement();
            statement.setLong(position, val);
        } catch (Exception e) { die("setLong: "+e, e); }
        return this;
    }

    @Override public Query<E> setFloat(int position, float val) {
        try {
            initStatement();
            statement.setFloat(position, val);
        } catch (Exception e) { die("setFloat: "+e, e); }
        return this;
    }

    @Override public Query<E> setDouble(int position, double val) {
        try {
            initStatement();
            statement.setDouble(position, val);
        } catch (Exception e) { die("setDouble: "+e, e); }
        return this;
    }

    @Override public Query<E> setBinary(int position, byte[] val) { return notSupported(); }

    @Override public Query<E> setText(int position, String val) {
        try {
            initStatement();
            statement.setString(position, val);
        } catch (Exception e) { die("setText: "+e, e); }
        return this;
    }

    @Override public Query<E> setSerializable(int position, Serializable val) { return notSupported(); }

    @Override public Query<E> setLocale(int position, Locale locale) { return notSupported(); }

    @Override public Query<E> setBigDecimal(int position, BigDecimal number) {
        try {
            initStatement();
            statement.setBigDecimal(position, number);
        } catch (Exception e) { die("setBigDecimal: "+e, e); }
        return this;
    }

    @Override public Query<E> setBigInteger(int position, BigInteger number) {
        try {
            initStatement();
            statement.setBigDecimal(position, new BigDecimal(number));
        } catch (Exception e) { die("setBigInteger: "+e, e); }
        return this;
    }

    @Override public Query<E> setEntity (int position, Object val) { return notSupported(); }
    @Override public Query<E> setEntity (String name, Object val) { return notSupported(); }
    @Override public Type determineProperBooleanType (int i, Object o, Type type) { return notSupported(); }
    @Override public Type determineProperBooleanType (String s, Object o, Type type) { return notSupported(); }
    @Override public Query<E> setResultTransformer (ResultTransformer transformer) { return notSupported(); }

}
