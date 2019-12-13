package org.cobbzilla.wizard.model.entityconfig.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
public @interface ECIndex {

    String name() default "";
    boolean unique() default false;
    String[] of() default {};
    String statement() default "";

}
