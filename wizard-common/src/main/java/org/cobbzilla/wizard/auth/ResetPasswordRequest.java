package org.cobbzilla.wizard.auth;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@Accessors(chain=true) @NoArgsConstructor @AllArgsConstructor
public class ResetPasswordRequest {

    @Getter @Setter private String token;
    @Getter @Setter private String password;

}
