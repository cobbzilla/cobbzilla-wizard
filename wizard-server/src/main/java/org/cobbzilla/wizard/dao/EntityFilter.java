package org.cobbzilla.wizard.dao;

public interface EntityFilter<E> {

    boolean isAcceptable (E entity);

}
