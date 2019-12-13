package org.cobbzilla.wizardtest.server.config;

import org.cobbzilla.wizard.server.config.RecaptchaConfig;

public class DummyRecaptchaConfig extends RecaptchaConfig {

    public static final DummyRecaptchaConfig instance = new DummyRecaptchaConfig();

    @Override public boolean verify(String captchaResponse) { return true; }

    @Override public RecaptchaVerificationResponse getVerificationResponse(String captchaResponse) throws Exception {
        return new DummyRecaptchaVerificationResponse();
    }

    private class DummyRecaptchaVerificationResponse extends RecaptchaVerificationResponse {
        @Override public boolean isSuccess() { return true; }
    }

}
