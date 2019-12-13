package org.cobbzilla.wizard.auth;

import lombok.Getter;
import lombok.ToString;

@ToString(callSuper=false)
public class AuthenticationException extends Exception {

    public enum Problem {
        INVALID, NOT_FOUND, BOOTCONFIG_ERROR, // login errors
        BOOTCONFIG_DIR_CREATE_ERROR, ACCOUNT_ALREADY_EXISTS // registration errors
    }

    public AuthenticationException(Problem problem) {
        this.problem = problem;
    }

    @Getter private final Problem problem;

}
