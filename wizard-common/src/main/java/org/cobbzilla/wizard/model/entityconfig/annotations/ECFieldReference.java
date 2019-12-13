package org.cobbzilla.wizard.model.entityconfig.annotations;

import org.cobbzilla.wizard.model.entityconfig.EntityFieldMode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.cobbzilla.wizard.model.entityconfig.EntityFieldReference.REF_PARENT;

@Retention(RetentionPolicy.RUNTIME) @Target({ElementType.FIELD, ElementType.METHOD})
public @interface ECFieldReference {
    EntityFieldMode mode() default EntityFieldMode.readOnly;
    String control() default "hidden";
    // `type` is `reference` of course!
    String options() default "";
    String displayName() default "";
    String emptyDisplayValue() default "";

    String refEntity() default REF_PARENT;
    String refField() default "uuid";
    String refDisplayField() default "name";
    String refFinder() default "";
}
