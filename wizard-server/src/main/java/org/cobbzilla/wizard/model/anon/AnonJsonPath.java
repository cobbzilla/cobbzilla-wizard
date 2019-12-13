package org.cobbzilla.wizard.model.anon;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Accessors(chain=true)
public class AnonJsonPath {

    @Getter @Setter private String path;
    @Setter private AnonType type;
    public AnonType getType() { return type != null ? type : AnonType.guessType(getPath()); }

}
