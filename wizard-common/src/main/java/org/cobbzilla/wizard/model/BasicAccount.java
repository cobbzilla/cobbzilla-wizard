package org.cobbzilla.wizard.model;

public interface BasicAccount extends Identifiable {

    Integer getAuthIdInt();

    String getName();

    boolean isTwoFactor();

    boolean isSuspended();

    void setLastLogin();

    String getEmailVerificationCode();

    boolean isEmailVerificationCodeValid(long expiration);

    void setEmailVerified(boolean verified);

    String initResetToken();
    String getResetToken();
    long getResetTokenAge();

    String getEmail();

    String getFullName();

    BasicAccount setPassword(String newPassword);

    void setResetToken(String token);

}
