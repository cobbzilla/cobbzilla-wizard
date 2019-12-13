package org.cobbzilla.wizard.dao.sql;

import com.google.common.base.CaseFormat;
import com.google.common.base.Converter;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.reflect.ReflectionUtil;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.server.config.DatabaseConfiguration;
import org.hibernate.*;
import org.hibernate.transform.ResultTransformer;
import org.hibernate.type.ObjectType;
import org.hibernate.type.Type;

import java.io.Closeable;
import java.io.IOException;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.*;
import java.util.*;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.notSupported;
import static org.cobbzilla.util.reflect.ReflectionUtil.instantiate;

@Slf4j @Accessors(chain=true)
public class ObjectSQLQuery<E extends Identifiable> implements Query, Closeable {

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

    @Getter private Integer maxResults;
    @Getter private Integer firstResult;
    @Getter private Integer fetchSize;
    @Getter private Integer timeout;
    @Getter private boolean readOnly = true;

    private Connection connection = null;
    private PreparedStatement statement = null;
    private ResultSet resultSet = null;

    public ObjectSQLQuery(DatabaseConfiguration database, String sql, Class<E> entityClass) {
        this.database = database;
        this.sql = sql;
        this.entityClass = entityClass;
    }

    public static String nParams(int ct) {
        final StringBuilder p = new StringBuilder();
        for (int i=0; i<ct; i++) {
            if (p.length() > 0) p.append(", ");
            p.append("?");
        }
        return p.toString();
    }

    @Override public void close() throws IOException {
        try { ReflectionUtil.close(resultSet);  } catch (Exception e) { log.warn("error closing ResultSet: "+e); }
        try { ReflectionUtil.close(statement);  } catch (Exception e) { log.warn("error closing PreparedStatement: "+e); }
        try { ReflectionUtil.close(connection); } catch (Exception e) { log.warn("error closing Connection: "+e); }
    }

    @Override public String getQueryString() { return sql; }

    @Override public Query setMaxResults (int maxResults)   { this.maxResults = maxResults; return this; }
    @Override public Query setFirstResult(int firstResult)  { this.firstResult = firstResult; return this; }
    @Override public Query setTimeout    (int timeout)      { this.timeout = timeout; return this; }
    @Override public Query setFetchSize  (int fetchSize)    { this.fetchSize = fetchSize; return this; }
    @Override public Query setReadOnly   (boolean readOnly) { this.readOnly = readOnly; return this; }

    @Override public FlushMode getFlushMode() { return FlushMode.AUTO; }
    @Override public Query setFlushMode(FlushMode flushMode) { return this; }

    @Override public CacheMode getCacheMode() { return CacheMode.IGNORE; }
    @Override public Query setCacheMode(CacheMode cacheMode) { return this; }

    @Override public boolean isCacheable() { return false; }

    @Override public Query setCacheable(boolean cacheable) { return this; }
    @Override public String getCacheRegion() { return null; }
    @Override public Query setCacheRegion(String cacheRegion) { return this; }

    @Override public Type[] getReturnTypes() { return new Type[] {ObjectType.INSTANCE}; }

    @Override public LockOptions getLockOptions() { return LockOptions.NONE; }
    @Override public Query setLockOptions(LockOptions lockOptions) { return this; }

    @Override public Query setLockMode(String alias, LockMode lockMode) { return this; }

    @Override public String getComment() { return null; }
    @Override public Query setComment(String comment) { return this; }

    @Override public Query addQueryHint(String hint) { return null; }

    @Override public String[] getReturnAliases() { return new String[] {entityClass.getSimpleName()}; }

    @Override public String[] getNamedParameters() { return new String[0]; }

    @Override public Iterator iterate() { return notSupported(); }
    @Override public ScrollableResults scroll() { return notSupported(); }
    @Override public ScrollableResults scroll(ScrollMode scrollMode) { return notSupported(); }

    @Override public List list() {
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

    @Override public Object uniqueResult() {
        setMaxResults(1);
        final List found = list();
        return found.isEmpty() ? null : found.get(0);
    }

    @Override public int executeUpdate() { return notSupported(); }

    @Override public Query setParameter(int position, Object val, Type type) { return notSupported(); }
    @Override public Query setParameter(String name, Object val, Type type) { return notSupported(); }
    @Override public Query setParameter(int position, Object val) { return notSupported(); }
    @Override public Query setParameter(String name, Object val) { return notSupported(); }
    @Override public Query setParameters(Object[] values, Type[] types) { return notSupported(); }
    @Override public Query setParameterList(String name, Collection values, Type type) { return notSupported(); }
    @Override public Query setParameterList(String name, Collection values) { return notSupported(); }
    @Override public Query setParameterList(String name, Object[] values, Type type) { return notSupported(); }
    @Override public Query setParameterList(String name, Object[] values) { return notSupported(); }
    @Override public Query setProperties(Object bean) { return notSupported(); }
    @Override public Query setProperties(Map bean) { return notSupported(); }

    @Override public Query setString(int position, String val) {
        try {
            initStatement();
            statement.setString(position, val);
        } catch (Exception e) { die("setString: "+e, e); }
        return this;
    }

    @Override public Query setCharacter(int position, char val) {
        try {
            initStatement();
            statement.setString(position, ""+val);
        } catch (Exception e) { die("setCharacter: "+e, e); }
        return this;
    }

    @Override public Query setBoolean(int position, boolean val) {
        try {
            initStatement();
            statement.setBoolean(position, val);
        } catch (Exception e) { die("setBoolean: "+e, e); }
        return this;
    }

    @Override public Query setByte(int position, byte val) {
        try {
            initStatement();
            statement.setByte(position, val);
        } catch (Exception e) { die("setByte: "+e, e); }
        return this;
    }

    @Override public Query setShort(int position, short val) {
        try {
            initStatement();
            statement.setShort(position, val);
        } catch (Exception e) { die("setShort: "+e, e); }
        return this;
    }

    @Override public Query setInteger(int position, int val) {
        try {
            initStatement();
            statement.setInt(position, val);
        } catch (Exception e) { die("setInteger: "+e, e); }
        return this;
    }

    @Override public Query setLong(int position, long val) {
        try {
            initStatement();
            statement.setLong(position, val);
        } catch (Exception e) { die("setLong: "+e, e); }
        return this;
    }

    @Override public Query setFloat(int position, float val) {
        try {
            initStatement();
            statement.setFloat(position, val);
        } catch (Exception e) { die("setFloat: "+e, e); }
        return this;
    }

    @Override public Query setDouble(int position, double val) {
        try {
            initStatement();
            statement.setDouble(position, val);
        } catch (Exception e) { die("setDouble: "+e, e); }
        return this;
    }

    @Override public Query setBinary(int position, byte[] val) { return notSupported(); }

    @Override public Query setText(int position, String val) {
        try {
            initStatement();
            statement.setString(position, val);
        } catch (Exception e) { die("setText: "+e, e); }
        return this;
    }

    @Override public Query setSerializable(int position, Serializable val) { return notSupported(); }

    @Override public Query setLocale(int position, Locale locale) { return notSupported(); }

    @Override public Query setBigDecimal(int position, BigDecimal number) {
        try {
            initStatement();
            statement.setBigDecimal(position, number);
        } catch (Exception e) { die("setBigDecimal: "+e, e); }
        return this;
    }

    @Override public Query setBigInteger(int position, BigInteger number) {
        try {
            initStatement();
            statement.setBigDecimal(position, new BigDecimal(number));
        } catch (Exception e) { die("setBigInteger: "+e, e); }
        return this;
    }

    @Override public Query setDate(int position, Date date) { return notSupported(); }
    @Override public Query setTime(int position, Date date) { return notSupported(); }
    @Override public Query setTimestamp(int position, Date date) { return notSupported(); }
    @Override public Query setCalendar(int position, Calendar calendar) { return notSupported(); }
    @Override public Query setCalendarDate(int position, Calendar calendar) { return notSupported(); }

    @Override public Query setString(String name, String val) { return notSupported(); }
    @Override public Query setCharacter(String name, char val) { return notSupported(); }
    @Override public Query setBoolean(String name, boolean val) { return notSupported(); }
    @Override public Query setByte(String name, byte val) { return notSupported(); }
    @Override public Query setShort(String name, short val) { return notSupported(); }
    @Override public Query setInteger(String name, int val) { return notSupported(); }
    @Override public Query setLong(String name, long val) { return notSupported(); }
    @Override public Query setFloat(String name, float val) { return notSupported(); }
    @Override public Query setDouble(String name, double val) { return notSupported(); }
    @Override public Query setBinary(String name, byte[] val) { return notSupported(); }
    @Override public Query setText(String name, String val) { return notSupported(); }
    @Override public Query setSerializable(String name, Serializable val) { return notSupported(); }
    @Override public Query setLocale(String name, Locale locale) { return notSupported(); }
    @Override public Query setBigDecimal(String name, BigDecimal number) { return notSupported(); }
    @Override public Query setBigInteger(String name, BigInteger number) { return notSupported(); }
    @Override public Query setDate(String name, Date date) { return notSupported(); }
    @Override public Query setTime(String name, Date date) { return notSupported(); }
    @Override public Query setTimestamp(String name, Date date) { return notSupported(); }
    @Override public Query setCalendar(String name, Calendar calendar) { return notSupported(); }
    @Override public Query setCalendarDate(String name, Calendar calendar) { return notSupported(); }
    @Override public Query setEntity(int position, Object val) { return notSupported(); }
    @Override public Query setEntity(String name, Object val) { return notSupported(); }

    @Override public Query setResultTransformer(ResultTransformer transformer) { return notSupported(); }

}
