package org.cobbzilla.wizard.docstore.mongo;

import lombok.Getter;
import org.cobbzilla.wizard.dao.DAO;
import org.cobbzilla.wizard.dao.SearchResults;
import org.cobbzilla.wizard.model.search.ResultPage;

import javax.validation.Valid;
import java.io.Serializable;
import java.util.List;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.reflect.ReflectionUtil.getFirstTypeParam;

/** a mongo docstore that also conforms to the DAO interface */
public abstract class MongoDocStoreDAOBase<T extends MongoDocBase> extends MongoDocStore<T> implements DAO<T> {

    @Getter(lazy=true) private final Class entityClass = getFirstTypeParam(getClass(), MongoDocBase.class);

    @Override public T get(Serializable id) { return findByUuid(id.toString()); }

    @Override public List<T> findAll() { return die("not supported"); }
    @Override public boolean exists(String uuid) { return findOne(MongoDocBase.UUID, uuid) != null; }

    @Override public Object preCreate(@Valid T entity) { return entity; }
    @Override public T postCreate(T entity, Object context) { return entity; }

    @Override public T create(@Valid T entity) {
        entity.beforeCreate();
        final Object context = preCreate(entity);
        save(entity);
        return postCreate(entity, context);
    }

    @Override public T createOrUpdate(@Valid T entity) {
        if (entity.getUuid() == null) entity.beforeCreate();
        saveOrUpdate(entity);
        return entity;
    }

    @Override public Object preUpdate(@Valid T entity) { return entity; }
    @Override public T postUpdate(@Valid T entity, Object context) { return entity; }

    @Override public T update(@Valid T entity) {
        if (entity.getUuid() == null) entity.beforeCreate();
        saveOrUpdate(entity);
        return entity;
    }

    @Override public void delete(String uuid) { delete(get(uuid).getId()); }

    @Override public T findByUniqueField(String field, Object value) {
        List<T> found = findByFilter(field, value);
        if (found.isEmpty()) return null;
        if (found.size() > 1) die("multiple results found for: field="+field+", value="+value+": "+found);
        return found.get(0);
    }

    @Override public SearchResults<T> search(ResultPage resultPage) {
        return search(resultPage, getEntityClass().getSimpleName());
    }

    @Override public SearchResults<T> search(ResultPage resultPage, String entityAlias) {
        // todo
        return new SearchResults<>();
    }
}
