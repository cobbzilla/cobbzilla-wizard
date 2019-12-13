package org.cobbzilla.wizard.validation;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({TYPE, ANNOTATION_TYPE})
@Retention(RUNTIME)
@Constraint(validatedBy = UniqueValidator.class)
@Documented
public @interface IsUnique {

    String CREATE_ONLY = "-create-only-";
    String DEFAULT = "-default-";
    String DEFAULT_ID_PROPERTY = "uuid";

    String idField () default DEFAULT;
    String id() default DEFAULT_ID_PROPERTY;
    String uniqueField () default DEFAULT;
    String unique();
    String daoBean ();

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    String message() default "{err.notUnique}";

    @Target({TYPE, ANNOTATION_TYPE})
    @Retention(RUNTIME)
    @Documented
    @interface List {
        IsUnique[] value();
    }

}
