package org.cobbzilla.wizard.docstore.mongo;

import com.github.jmkgreen.morphia.Datastore;
import com.github.jmkgreen.morphia.Morphia;
import com.github.jmkgreen.morphia.query.Query;
import com.mongodb.Mongo;
import lombok.Getter;
import org.cobbzilla.util.reflect.ReflectionUtil;
import org.cobbzilla.wizard.docstore.DocStore;
import org.cobbzilla.wizard.server.config.DocStoreConfiguration;

import java.util.List;

public abstract class MongoDocStore<T extends MongoDocBase> implements DocStore<T> {

    protected abstract DocStoreConfiguration configuration();

    private Class<T> clazz;

    @Getter(lazy=true) private final Datastore datastore = initDatastore();

    private Datastore initDatastore () {
        this.clazz = ReflectionUtil.getTypeParameter(getClass(), MongoDocBase.class);
        Mongo mongo;
        try {
            mongo = new Mongo(configuration().getHost(), configuration().getPort());
        } catch (Exception e) {
            throw new IllegalArgumentException("Error instantiating MongoDocStore<"+clazz.getSimpleName()+">: "+e, e);
        }
        Morphia morphia = new Morphia();
        for (String entityPackage : configuration().getEntityPackages()) {
            morphia.mapPackage(entityPackage);
        }
        Datastore datastore = morphia.createDatastore(mongo, configuration().getDbName());
        datastore.ensureIndexes();
        return datastore;
    }

    @Override
    public T findOne(String field, Object value) { return getDatastore().find(clazz, field, value).get(); }

    public T findByUuid (String uuid) { return findOne(MongoDocBase.UUID, uuid); }

    @Override
    public List<T> findByFilter(String field, Object value) {
        return getDatastore().createQuery(clazz).field(field).equal(value).asList();
    }

    @Override public void save(T thing) { getDatastore().save(thing); }

    public void saveOrUpdate (T thing) {

        final String uuid = thing.getUuid();
        if (uuid == null) throw new IllegalArgumentException("uuid was null");

        final T found = findByUuid(uuid);
        if (found == null) {
            save(thing);
        } else {
            thing.setId(found.getId());
            getDatastore().merge(thing);
        }
    }

    @Override public void delete(Object id) { getDatastore().delete(clazz, id); }

    @Override
    public void deleteByFilter(String field, Object value) {
        getDatastore().delete(getDatastore().createQuery(clazz).filter(field, value));
    }

    @Override
    public void deleteByFilter(String[][] criteria) {
        Query<T> query = getDatastore().createQuery(clazz);
        for (String[] criterion : criteria) {
            query = query.filter(criterion[0], criterion[1]);
        }
        getDatastore().delete(query);
    }
}
