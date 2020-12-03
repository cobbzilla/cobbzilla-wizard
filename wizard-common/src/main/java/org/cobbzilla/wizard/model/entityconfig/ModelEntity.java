package org.cobbzilla.wizard.model.entityconfig;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.cobbzilla.wizard.model.Identifiable;

public interface ModelEntity extends Identifiable {
    ObjectNode jsonNode();
    void updateNode();
    boolean forceUpdate();
    boolean performSubstitutions();
    Identifiable getEntity();
    boolean hasData(final boolean strict);
}
