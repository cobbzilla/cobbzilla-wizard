package org.cobbzilla.wizard.model.anonymize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME) @Target(value = {ElementType.FIELD, ElementType.METHOD, ElementType.TYPE})
public @interface AnonymizeType {
    String name() default  "";
    boolean encrypted() default false;
    String type() default "";
    String[] skip() default {};
    String value() default "";
    AnonymizeJsonPath[] json() default {};
}
