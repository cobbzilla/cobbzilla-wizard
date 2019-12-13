package org.cobbzilla.wizard.validation;

// forked from dropwizard-- https://github.com/codahale/dropwizard
public class InvalidEntityException extends RuntimeException {
    private static final long serialVersionUID = -8762073181655035705L;

    private final ValidationResult result;

    public InvalidEntityException(String message, ValidationResult result) {
        super(message);
        this.result = result;
    }

    public ValidationResult getResult() { return result; }

}
