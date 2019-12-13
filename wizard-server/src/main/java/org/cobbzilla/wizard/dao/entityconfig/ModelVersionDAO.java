package org.cobbzilla.wizard.dao.entityconfig;

import org.cobbzilla.util.reflect.ReflectionUtil;
import org.cobbzilla.wizard.dao.AbstractCRUDDAO;
import org.cobbzilla.wizard.model.entityconfig.ModelVersion;
import org.hibernate.criterion.Order;

import java.util.List;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

public class ModelVersionDAO<V extends ModelVersion> extends AbstractCRUDDAO<V> {

    @Override public Order getDefaultSortOrder() { return Order.asc("version"); }

    @Override protected int getFinderMaxResults() { return Integer.MAX_VALUE; }

    public V findByUuidOrVersion(Object id) {
        // if id is a number try to lookup version
        V version = null;
        try {
            version = findByUniqueField("version", ReflectionUtil.toInteger(id));
        } catch (Exception ignored) {}
        return version != null ? version : findByUuid(id.toString());
    }

    public V findCurrentVersion() {
        final List<V> all = findAll();
        return empty(all) ? null : all.get(all.size()-1);
    }

}
