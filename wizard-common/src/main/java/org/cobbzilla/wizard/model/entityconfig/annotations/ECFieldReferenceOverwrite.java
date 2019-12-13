package org.cobbzilla.wizard.model.entityconfig.annotations;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE) @Repeatable(value=ECFieldReferenceOverwrites.class)
public @interface ECFieldReferenceOverwrite {
    String fieldPath(); // field's path through the children tree (dot separated), or annotated class field's name.
    ECFieldReference fieldDef();
}
