package org.cobbzilla.wizard.model.entityconfig.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
public @interface ECType {
    boolean root() default false;
    String name() default "";
    String displayName() default "";
    String pluralDisplayName() default "";
    boolean useOriginalJsonRequestOnly() default false;
}
