package org.cobbzilla.wizard.auth;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.wizard.validation.HasValue;

@NoArgsConstructor @Accessors(chain=true)
public class ChangePasswordRequest {

    @Getter @Setter private String uuid;

    @HasValue(message="error.changePassword.oldPassword.required")
    @Getter @Setter private String oldPassword;

    @HasValue(message="error.changePassword.newPassword.required")
    @Getter @Setter private String newPassword;

    @Getter @Setter private boolean sendInvite = false;

    public ChangePasswordRequest (String oldPassword, String newPassword) {
        setOldPassword(oldPassword);
        setNewPassword(newPassword);
    }
}
