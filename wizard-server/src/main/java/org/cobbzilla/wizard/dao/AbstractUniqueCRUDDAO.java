package org.cobbzilla.wizard.dao;

import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.validation.UniqueValidatorDao;
import org.cobbzilla.wizard.validation.UniqueValidatorDaoHelper;

import java.util.Map;

public abstract class AbstractUniqueCRUDDAO<T extends Identifiable> extends AbstractCRUDDAO<T> implements UniqueValidatorDao {

    private UniqueValidatorDaoHelper<T> uniqueHelper = new UniqueValidatorDaoHelper<>(getUniqueHelpers());

    protected abstract Map<String, UniqueValidatorDaoHelper.Finder<T>> getUniqueHelpers();

    protected abstract T findByName(String name);

    @Override public boolean isUnique(String uniqueFieldName, Object uniqueValue) {
        return uniqueHelper.isUnique(uniqueFieldName, uniqueValue);
    }

    @Override public boolean isUnique(String uniqueFieldName, Object uniqueValue, String idFieldName, Object idValue) {
        return uniqueHelper.isUnique(uniqueFieldName, uniqueValue, idFieldName, idValue);
    }

}
