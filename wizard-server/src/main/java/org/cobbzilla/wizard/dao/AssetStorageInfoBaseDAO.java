package org.cobbzilla.wizard.dao;

import org.cobbzilla.wizard.model.Identifiable;

import java.util.List;

public interface AssetStorageInfoBaseDAO<T extends Identifiable,
                                         RT extends Identifiable,
                                         RD extends DAO<RT>> extends DAO<T> {

    default List<T> findByRelated(String uuid) { return findByField("relatedEntity", uuid); }

}
