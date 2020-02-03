package org.cobbzilla.wizard.model.entityconfig.annotations;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum EntityFieldRequired {

    required (true), optional (false), unset (null);

    private final Boolean booleanValue;

    @JsonCreator public static EntityFieldRequired fromString (String v) { return valueOf(v.toLowerCase()); }

    public Boolean bool() { return booleanValue; }

}
