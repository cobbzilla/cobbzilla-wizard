package org.cobbzilla.wizard.auth;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.wizard.validation.HasValue;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

@NoArgsConstructor @Accessors(chain=true)
public class ChangePasswordRequest {

    @Getter @Setter private String uuid;

    @HasValue(message="err.currentPassword.required")
    @Getter @Setter private String oldPassword;

    @HasValue(message="err.password.required")
    @Getter @Setter private String newPassword;

    @Getter @Setter private String totpToken;
    public boolean hasTotpToken () { return !empty(totpToken); }

    @Getter @Setter private boolean sendInvite = false;

    public ChangePasswordRequest (String oldPassword, String newPassword) {
        setOldPassword(oldPassword);
        setNewPassword(newPassword);
    }
}
