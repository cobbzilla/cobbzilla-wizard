package org.cobbzilla.wizard.validation;

import org.cobbzilla.util.string.ResourceMessages;

public class ValidationMessages extends ResourceMessages {

    private static final ValidationMessages instance = new ValidationMessages();

    public static final String BUNDLE_NAME = ValidationMessages.class.getSimpleName();

    @Override public String getBundleName() { return BUNDLE_NAME; }

    public static String translateMessage(String messageTemplate) { return instance.translate(messageTemplate); }

}
