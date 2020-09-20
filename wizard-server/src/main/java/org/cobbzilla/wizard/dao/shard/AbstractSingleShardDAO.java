package org.cobbzilla.wizard.dao.shard;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.reflect.ReflectionUtil;
import org.cobbzilla.wizard.dao.AbstractCRUDDAO;
import org.cobbzilla.wizard.dao.shard.task.ShardSearchTask;
import org.cobbzilla.wizard.dao.sql.ObjectSQLQuery;
import org.cobbzilla.wizard.model.shard.ShardMap;
import org.cobbzilla.wizard.model.shard.Shardable;
import org.cobbzilla.wizard.server.config.HasDatabaseConfiguration;
import org.cobbzilla.wizard.server.config.RestServerConfiguration;
import org.hibernate.SessionFactory;
import org.hibernate.StatelessSession;
import org.hibernate.query.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.AbstractApplicationContext;

import java.io.Closeable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;

@Slf4j
public abstract class AbstractSingleShardDAO<E extends Shardable> extends AbstractCRUDDAO<E>
        implements SingleShardDAO<E> {

    @SuppressWarnings("SpringJavaAutowiredMembersInspection")
    @Autowired private RestServerConfiguration configuration;

    @Getter @Setter private ShardMap shard;

    @Override public <R> List<R> search(ShardSearch search) {
        return new ShardSearchTask(this, search).execTask();
    }

    @Override public List query(int maxResults, String hsql, Object... args) {
        return query(maxResults, hsql, Arrays.asList(args));
    }

    @Override public List query(int maxResults, String hsql, List<Object> args) {
        boolean isSql = false;
        if (hsql.startsWith(SQL_QUERY)) {
            isSql = true;
            hsql = hsql.substring(SQL_QUERY.length());
        }
        final SessionFactory factory = getHibernateTemplate().getSessionFactory();
        StatelessSession session = null;
        Query query = null;
        try {
            session = factory.openStatelessSession();
            if (isSql) {
                final HasDatabaseConfiguration dbconfig = (HasDatabaseConfiguration) this.configuration;
                query = new ObjectSQLQuery<>(dbconfig.getDatabase(), hsql, getEntityClass());
            } else {
                query = session.createQuery(hsql);
            }
            query.setMaxResults(maxResults);
            int i = isSql ? 1 : 0;
            for (Object arg : args) {
                if (arg == null) {
                    die("query: null values not supported");
                } else if (arg instanceof String) {
                    query.setString(i++, arg.toString());
                } else if (arg instanceof BigDecimal) {
                    query.setBigDecimal(i++, (BigDecimal) arg);
                } else if (arg instanceof BigInteger) {
                    query.setBigInteger(i++, (BigInteger) arg);
                } else if (arg instanceof Double) {
                    query.setDouble(i++, (Double) arg);
                } else {
                    die("query: unsupported argument type: " + arg);
                }
            }
            return query.list();

        } finally {
            if (query instanceof Closeable) ReflectionUtil.closeQuietly(query);
            if (session != null) session.close();
        }
    }

    @Override public void initialize(ShardMap map) { setShard(map); }

    @Override public void cleanup() {
        try {
            Objects.requireNonNull(getHibernateTemplate().getSessionFactory()).close();
        } catch (Exception e) {
            log.warn("cleanup: error destroying session factory: "+e, e);
        }
        try {
            ((AbstractApplicationContext) configuration.getApplicationContext()).close();
        } catch (Exception e) {
            log.warn("cleanup: error destroying Spring ApplicationContext: "+e, e);
        }
    }

}
