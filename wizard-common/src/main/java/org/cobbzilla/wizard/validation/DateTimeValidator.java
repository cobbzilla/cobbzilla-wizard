package org.cobbzilla.wizard.validation;

import org.joda.time.format.DateTimeFormat;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class DateTimeValidator implements ConstraintValidator<ValidDateTime, String> {

    private String pattern;
    private boolean emptyOk;

    public void initialize(ValidDateTime constraintAnnotation) {
        pattern = constraintAnnotation.pattern();
        emptyOk = constraintAnnotation.emptyOk();
    }

    public boolean isValid(String object,
                           ConstraintValidatorContext constraintContext) {
        if ((object == null || object.trim().length() == 0) && emptyOk) return true;
        try {
            DateTimeFormat.forPattern(pattern).parseDateTime(object);
            return true;

        } catch (Exception e) {
            return false;
        }
    }

}