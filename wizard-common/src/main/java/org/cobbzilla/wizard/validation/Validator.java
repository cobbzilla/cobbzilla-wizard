package org.cobbzilla.wizard.validation;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import javax.validation.groups.Default;
import java.util.ArrayList;
import java.util.List;

import static org.hibernate.validator.internal.engine.ConstraintViolationImpl.forReturnValueValidation;

// forked from dropwizard-- https://github.com/codahale/dropwizard

/**
 * A simple facade for Hibernate Validator.
 * forked from DropWizard-- https://github.com/codahale/dropwizard
 */
public class Validator {
    private final ValidatorFactory factory;

    public Validator() { this(Validation.buildDefaultValidatorFactory()); }

    public Validator(ValidatorFactory factory) {
        this.factory = factory;
    }

    /**
     * Validates the given object, and returns a list of error messages, if any. If the returned
     * list is empty, the object is valid.
     *
     * @param o      a potentially-valid object
     * @return a list of error messages, if any, regarding {@code o}'s validity
     */
    public ValidationResult validate(Object o) { return validate(o, Default.class); }

    /**
     * Validates the given object, and returns a list of error messages, if any. If the returned
     * list is empty, the object is valid.
     *
     * @param o a potentially-valid object
     * @param groups group or list of groups targeted for validation (default to {@link javax.validation.groups.Default})
     * @return a list of error messages, if any, regarding {@code o}'s validity
     */
    public ValidationResult validate(Object o, Class<?>... groups) {
        final List<ConstraintViolation> violations = new ArrayList<>();
        if (o == null) {
            violations.add(forReturnValueValidation("err.requestEntity.null", null, null, null, null, null, null, null,
                                                    null, null, null, null));
        } else {
            violations.addAll(factory.getValidator().validate(o,groups));
        }
        return new ValidationResult(violations);
    }
}
