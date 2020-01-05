package org.cobbzilla.wizard.model.entityconfig;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.wizard.model.Identifiable;

import java.util.List;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

@Accessors(chain=true)
public class ModelDiffEntry {

    public ModelDiffEntry(String entityId) { this.entityId = entityId.startsWith("/") ? entityId.substring(1) : entityId; }

    @Getter private final String entityId;
    @Getter @Setter private String jsonDiff;
    @Getter @Setter private List<String> deltas;
    @Getter @Setter private Identifiable createEntity;

    public boolean isEmpty() { return empty(jsonDiff) && empty(deltas) && empty(createEntity); }

}
