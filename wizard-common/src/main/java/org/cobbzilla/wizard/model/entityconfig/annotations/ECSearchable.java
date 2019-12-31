package org.cobbzilla.wizard.model.entityconfig.annotations;

import org.cobbzilla.wizard.model.entityconfig.EntityFieldType;
import org.cobbzilla.wizard.model.search.SqlViewFieldSetter;
import org.jasypt.hibernate4.encryptor.HibernatePBEStringEncryptor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.cobbzilla.util.daemon.ZillaRuntime.notSupported;

@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
public @interface ECSearchable {

    boolean filter() default false;
    String property() default "";
    Class<? extends SqlViewFieldSetter> setter() default DefaultSqlViewFieldSetter.class;
    String sortField() default "";
    EntityFieldType type() default EntityFieldType.none_set;
    String bounds() default "";
    String entity() default "";

    class DefaultSqlViewFieldSetter implements SqlViewFieldSetter {
        @Override public void set(Object target, String entityProperty, Object value, HibernatePBEStringEncryptor hibernateEncryptor) {
            notSupported("set");
        }
    }
}
