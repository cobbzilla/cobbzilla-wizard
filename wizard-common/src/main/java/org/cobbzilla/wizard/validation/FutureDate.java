package org.cobbzilla.wizard.validation;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({FIELD, ANNOTATION_TYPE})
@Retention(RUNTIME)
@Constraint(validatedBy=FutureDateValidator.class)
@Documented
public @interface FutureDate {

    String min () default "";

    String format () default "";
    boolean emptyOk () default false;
    TimeUnit resolution () default TimeUnit.MILLISECONDS;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    String message() default "{err.futureDate}";

}
