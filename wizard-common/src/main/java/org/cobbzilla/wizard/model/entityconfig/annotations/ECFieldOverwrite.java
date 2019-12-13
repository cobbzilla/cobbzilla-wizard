package org.cobbzilla.wizard.model.entityconfig.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
public @interface ECFieldOverwrite {
    String fieldPath(); // field's path through the children tree (dot separated), or annotated class field's name.
    ECField fieldDef();
}
