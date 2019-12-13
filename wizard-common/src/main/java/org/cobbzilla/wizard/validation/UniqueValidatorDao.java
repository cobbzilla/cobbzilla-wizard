package org.cobbzilla.wizard.validation;

public interface UniqueValidatorDao {

    /** Return true if an entity exists where uniqueFieldName=uniqueValue */
    public boolean isUnique(String uniqueFieldName, Object uniqueValue);

    /** Return true if an entity exists where uniqueFieldName=uniqueValue, and idFieldName != idValue */
    public boolean isUnique(String uniqueFieldName, Object uniqueValue, String idFieldName, Object idValue);

}
