package org.cobbzilla.wizard.model.entityconfig;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.collections.IteratorUtils;
import org.apache.commons.lang.ArrayUtils;
import org.cobbzilla.wizard.model.Identifiable;

public interface ModelEntity extends Identifiable {
    ObjectNode jsonNode();
    void updateNode();
    boolean forceUpdate();
    boolean performSubstitutions();
    Identifiable getEntity();
    boolean hasData(final boolean strict);
}
