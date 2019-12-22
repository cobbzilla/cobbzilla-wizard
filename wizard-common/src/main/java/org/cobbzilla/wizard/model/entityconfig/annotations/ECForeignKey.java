package org.cobbzilla.wizard.model.entityconfig.annotations;

import org.cobbzilla.wizard.model.Identifiable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
public @interface ECForeignKey {

    Class<? extends Identifiable> entity();
    String field() default "uuid";
    boolean index() default true;
    boolean cascade() default true;

}
