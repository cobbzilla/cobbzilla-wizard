package org.cobbzilla.wizard.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.cobbzilla.wizard.model.Identifiable;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

@NoArgsConstructor @AllArgsConstructor
public abstract class AuthResponse<T extends Identifiable> {

    @Getter @Setter private String sessionId;
    @Getter @Setter private T account;

    public boolean hasSessionId() { return !empty(sessionId) && !isTwoFactor(); }

    public static final String TWO_FACTOR_SID = "2-factor";

    @JsonIgnore public boolean isTwoFactor () { return TWO_FACTOR_SID.equals(sessionId); }

}
