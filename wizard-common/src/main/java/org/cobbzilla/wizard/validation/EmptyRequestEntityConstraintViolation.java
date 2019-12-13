package org.cobbzilla.wizard.validation;

import org.hibernate.validator.internal.engine.ConstraintViolationImpl;

import javax.validation.ConstraintViolation;

public class EmptyRequestEntityConstraintViolation extends ConstraintViolationImpl {

    public static final ConstraintViolation INSTANCE = new EmptyRequestEntityConstraintViolation();

    public static final String MESSAGE_TEMPLATE = "err.requestEntity.null";

    public EmptyRequestEntityConstraintViolation() {
        super(MESSAGE_TEMPLATE, MESSAGE_TEMPLATE, null, null, null, null, null, null, null);
    }
}
