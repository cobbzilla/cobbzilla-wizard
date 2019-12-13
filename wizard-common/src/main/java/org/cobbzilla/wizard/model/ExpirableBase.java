package org.cobbzilla.wizard.model;

import lombok.Getter;
import lombok.Setter;

public abstract class ExpirableBase extends IdentifiableBase {

    @Getter @Setter private Long expirationSeconds;

    public boolean shouldExpire () { return expirationSeconds != null; }

}
