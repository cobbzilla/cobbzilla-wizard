package org.cobbzilla.wizard.model.entityconfig.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
public @interface ECTypeCreate {
    String DEFAULT_METHOD = "PUT";

    String method() default DEFAULT_METHOD;
    String uri() default "";
}
