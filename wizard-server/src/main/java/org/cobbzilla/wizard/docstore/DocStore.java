package org.cobbzilla.wizard.docstore;

import java.util.List;

public interface DocStore<T> {

    public T findOne(String field, Object value);

    public List<T> findByFilter(String field, Object value);

    public void save(T thing);

    public void delete(Object id);

    public void deleteByFilter(String field, Object value);

    public void deleteByFilter(String[][] criteria);
}
