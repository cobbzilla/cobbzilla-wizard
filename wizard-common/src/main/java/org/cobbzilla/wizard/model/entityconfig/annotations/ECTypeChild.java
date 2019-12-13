package org.cobbzilla.wizard.model.entityconfig.annotations;

import org.cobbzilla.wizard.model.Identifiable;

import java.lang.annotation.*;


@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE) @Repeatable(value=ECTypeChildren.class)
public @interface ECTypeChild {
    Class<? extends Identifiable> type();
    String name() default "";
    String displayName() default "";
    String backref();
    ECFieldReference parentFieldRef() default @ECFieldReference();
}
