package org.cobbzilla.wizard.model.anonymize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
public @interface AnonymizeTable {
    String name() default "";
    boolean truncate() default false;
}
