package org.cobbzilla.wizard.validation;


import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target( { METHOD, FIELD, ANNOTATION_TYPE })
@Retention(RUNTIME)
@Constraint(validatedBy = DateTimeValidator.class)
@Documented
public @interface ValidDateTime {

    String pattern() default "MM/dd/yyyy HH:mm";

    String message() default "{validator.dateTime}";

    boolean emptyOk() default false;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}