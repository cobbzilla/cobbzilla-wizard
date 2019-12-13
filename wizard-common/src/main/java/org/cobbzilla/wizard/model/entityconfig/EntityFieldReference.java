package org.cobbzilla.wizard.model.entityconfig;

import lombok.Getter;
import lombok.Setter;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

/**
 * When the EntityFieldType of a field is 'reference', this object is also attached to the field to describe
 * how to reach the reference.
 */
public class EntityFieldReference {

    /** A special value that can be used by child entities to indicate that the lexically enclosing entity is their parent. */
    public static final String REF_PARENT = ":parent";

    /** Name of the entity class of the parent */
    @Getter @Setter private String entity;

    /** Name of the field within the parent entity class that is referenced by this field */
    @Getter @Setter private String field;

    @Setter private String displayField;
    /** Display name of the field within the parent entity to use instead of displaying the raw identifier */
    public String getDisplayField() { return empty(displayField) ? field : displayField; }

    /**
     * API endpoint to use when looking up the parent. Useful when the entity is not a ':parent'
     * The API URI may include parameters in the form {parameter}. These parameters can reference fields
     * of the entity, or any of its parents.
     */
    @Getter @Setter private String finder;

}
