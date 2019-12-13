package org.cobbzilla.wizard.validation;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

public class HasValueValidator implements ConstraintValidator<HasValue, Object> {

    @Override public void initialize(HasValue constraintAnnotation) {}

    @Override public boolean isValid(Object value, ConstraintValidatorContext context) {
        return !empty(value) && value.toString().trim().length() > 0;
    }

}
