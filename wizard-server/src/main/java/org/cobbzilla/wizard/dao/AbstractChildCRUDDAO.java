package org.cobbzilla.wizard.dao;

import org.cobbzilla.wizard.model.ChildEntity;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.cobbzilla.wizard.model.Identifiable.UUID;

public abstract class AbstractChildCRUDDAO<C extends ChildEntity, P> extends AbstractDAO<C> {

    @Autowired protected SessionFactory sessionFactory;

    private final Class<P> parentEntityClass;

    public AbstractChildCRUDDAO(Class<P> parentEntityClass) {
        this.parentEntityClass = parentEntityClass;
    }

    @Override public C findByUuid(String uuid) {
        return uniqueResult(Restrictions.eq(UUID, uuid));
    }

    @Override public C findByUniqueField(String field, Object value) {
        return uniqueResult(Restrictions.eq(field, value));
    }

    public List<C> findByParentUuid(String parentUuid) {
        final String queryString = "from " + getEntityClass().getSimpleName() + " x where x." + parentEntityClass.getSimpleName().toLowerCase() + ".uuid=? order by x.ctime";
        return (List<C>) getHibernateTemplate().find(queryString, parentUuid);
    }

    public Map<String, C> mapChildrenOfParentByUuid(String parentUuid) {
        return mapChildrenOfParentByUuid(findByParentUuid(parentUuid));
    }

    public Map<String, C> mapChildrenOfParentByUuid(List<C> recordList) {
        Map<String, C> records = new HashMap<>(recordList.size());
        for (C record : recordList) {
            records.put(record.getUuid(), record);
        }
        return records;
    }

    private P findParentByUuid(String parentId) {
        return (P) uniqueResult(criteria(parentEntityClass).add(Restrictions.eq(UUID, parentId)));
    }

    public C create(String parentUuid, @Valid C child) {
        P parent = findParentByUuid(parentUuid);
        child.setParent(checkNotNull(parent));
        return create(child);
    }

    @Override public C create(@Valid C child) {
        child.beforeCreate();
        child.setUuid((String) getHibernateTemplate().save(checkNotNull(child)));
        return child;
    }

    @Override public C update(@Valid C child) {
        getHibernateTemplate().update(checkNotNull(child));
        return child;
    }

    @Override public void delete(String uuid) {
        C found = get(checkNotNull(uuid));
        if (found != null) {
            getHibernateTemplate().delete(found);
        }
    }

}
