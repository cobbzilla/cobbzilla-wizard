package org.cobbzilla.wizard.model.entityconfig.annotations;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ECForeignKeySearchDepth {

    inherit, none, shallow, deep;

    @JsonCreator public static ECForeignKeySearchDepth fromString (String v) { return valueOf(v.toLowerCase()); }

}
