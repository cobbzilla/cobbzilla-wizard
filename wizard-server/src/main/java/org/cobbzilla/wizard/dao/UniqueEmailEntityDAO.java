package org.cobbzilla.wizard.dao;

import org.cobbzilla.util.collection.MapBuilder;
import org.cobbzilla.wizard.model.UniqueEmailEntity;
import org.cobbzilla.wizard.validation.UniqueValidatorDaoHelper;

import java.util.Map;

public abstract class UniqueEmailEntityDAO<E extends UniqueEmailEntity> extends AbstractUniqueCRUDDAO<E> {

    public E findByEmail (String email) { return findByUniqueField("email", email.toLowerCase()); }

    @Override public E findByName (String email) { return findByEmail(email); }

    protected Map<String, UniqueValidatorDaoHelper.Finder<E>> getUniqueHelpers() {
        return MapBuilder.build(new Object[][]{
            {"email", new UniqueValidatorDaoHelper.Finder<E>() {
                @Override public E find(Object query) { return findByEmail(query.toString()); }
            }}
        });
    }

}
