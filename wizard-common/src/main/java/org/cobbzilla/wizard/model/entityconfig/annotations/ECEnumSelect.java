package org.cobbzilla.wizard.model.entityconfig.annotations;

import org.cobbzilla.wizard.model.entityconfig.EntityFieldMode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME) @Target({ElementType.FIELD, ElementType.TYPE})
public @interface ECEnumSelect {
    EntityFieldMode mode() default EntityFieldMode.standard;
    String displayName() default "";
    String options();
}
