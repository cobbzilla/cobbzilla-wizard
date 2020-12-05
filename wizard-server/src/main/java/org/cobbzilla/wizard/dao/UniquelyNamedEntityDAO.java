package org.cobbzilla.wizard.dao;

import org.cobbzilla.util.collection.MapBuilder;
import org.cobbzilla.wizard.model.UniquelyNamedEntity;
import org.cobbzilla.wizard.validation.UniqueValidatorDaoHelper;

import java.io.Serializable;
import java.util.Map;

import static org.cobbzilla.util.string.StringUtil.urlDecode;

public abstract class UniquelyNamedEntityDAO<E extends UniquelyNamedEntity> extends AbstractUniqueCRUDDAO<E> {

    @Override public E get(Serializable id) {
        E found = findByUuid(id.toString());
        return found != null ? found : findByName(id.toString());
    }

    public boolean forceLowercase () { return getEntityProto().forceLowercase(); }

    public E findByName (String name) { return findByUniqueField("name", nameValue(name)); }

    protected String nameValue(String name) { return forceLowercase() ? name.toLowerCase() : name; }

    protected Map<String, UniqueValidatorDaoHelper.Finder<E>> getUniqueHelpers() {
        return MapBuilder.build(new Object[][]{
            {"name", new UniqueValidatorDaoHelper.Finder<E>() { @Override public E find(Object query) { return findByName(query.toString()); } }}
        });
    }

    public E findByUuidOrName(String id) {
        E found = findByUuid(id);
        if (found == null) found = findByName(id);
        if (found == null) found = findByName(urlDecode(id));
        return found;
    }

}
