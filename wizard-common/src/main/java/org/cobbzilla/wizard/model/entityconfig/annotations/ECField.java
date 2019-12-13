package org.cobbzilla.wizard.model.entityconfig.annotations;

import org.cobbzilla.wizard.model.entityconfig.EntityFieldControl;
import org.cobbzilla.wizard.model.entityconfig.EntityFieldMode;
import org.cobbzilla.wizard.model.entityconfig.EntityFieldType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation that is directly related to the {@link org.cobbzilla.wizard.model.entityconfig.EntityFieldConfig}.
 */
@Retention(RetentionPolicy.RUNTIME) @Target({ElementType.FIELD, ElementType.METHOD})
public @interface ECField {
    String name() default "";
    String displayName() default "";
    EntityFieldMode mode() default EntityFieldMode.standard;
    EntityFieldType type() default EntityFieldType.string;
    int length() default -1;
    EntityFieldControl control() default EntityFieldControl.unset;
    String options() default "";
    String emptyDisplayValue() default "";
    String objectType() default "";

    // Skipping EntityFieldReference property from the original EC class, as there is
    // org.cobbzilla.wizard.model.entityconfig.annotations.ECFieldReference annotation for this.
}
