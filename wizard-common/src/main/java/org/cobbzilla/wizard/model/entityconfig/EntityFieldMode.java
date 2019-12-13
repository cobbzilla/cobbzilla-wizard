package org.cobbzilla.wizard.model.entityconfig;

import com.fasterxml.jackson.annotation.JsonCreator;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;

/**
 * Specifies how a field can (or cannot) be edited.
 * <ul><li>
 * standard fields are always editable.
 * </li><li>
 * createOnly fields are fixed at creation.
 * </li><li>
 * readOnly fields can never be edited.
 * </li></ul>
 */
public enum EntityFieldMode {

    /** field can be set at creation and edited thereafter */
    standard,

    /** field can be set at creation and cannot be edited thereafter */
    createOnly,

    /** field is read-only, it cannot be edited. useful for derived fields. */
    readOnly;

    /** Jackson-hook to create a new instance based on a string, case-insensitively */
    @JsonCreator public static EntityFieldMode create (String val) {
        for (EntityFieldMode m : values()) if (m.name().equalsIgnoreCase(val)) return m;
        return die("create("+val+"): invalid");
    }

}
