package org.cobbzilla.wizard.validation;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class EnumValidator implements ConstraintValidator<ValidEnum, String> {

    private Class type;
    private boolean emptyOk;

    @Override
    public void initialize(ValidEnum constraintAnnotation) {
        this.type = constraintAnnotation.type();
        this.emptyOk = constraintAnnotation.emptyOk();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (emptyOk && (value == null || value.trim().length() == 0)) return true;
        try {
            type.getMethod("valueOf", String.class).invoke(null, value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
