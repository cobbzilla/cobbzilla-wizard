package org.cobbzilla.wizard.model;

public interface ParentAndSuccessor extends Identifiable {

    String getParent ();
    boolean hasParent ();

    String getSuccessor ();
    boolean hasSuccessor();

}
