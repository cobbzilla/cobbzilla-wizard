package org.cobbzilla.wizard.dao;

public interface EntityFilter<E> {

    public boolean isAcceptable (E entity);

}
