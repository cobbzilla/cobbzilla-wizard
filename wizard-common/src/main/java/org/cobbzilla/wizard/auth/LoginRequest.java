package org.cobbzilla.wizard.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

@NoArgsConstructor @Accessors(chain=true)
public class LoginRequest {

    public LoginRequest (String name, String password) {
        this.name = name;
        this.password = password;
    }

    public boolean forceLowercase () { return true; }

    @Setter private String name;
    public String getName () { return name == null ? null : (forceLowercase() ? name.toLowerCase() : name).trim(); }
    public boolean hasName () { return !empty(name); }

    public String getUsername () { return getName(); }
    public LoginRequest setUsername (String name) { return name == null ? null : setName(name); }

    @Getter @Setter @JsonProperty private String password;
    public boolean hasPassword () { return !empty(password); }

    @Getter @Setter @JsonProperty private String totpToken;
    @JsonIgnore public boolean hasTotpToken() { return !empty(totpToken); }

    @Getter @Setter private String device;
    @JsonIgnore public boolean hasDevice () { return !empty(device); }

    // optional - server-side resource can fill this in for other server-side code to use
    @JsonIgnore @Getter @Setter private String userAgent;

    public String toString () {
        return "{name="+getName()+", password="+mask(password)+", totpToken="+mask(totpToken)+", device="+getDevice()+"}";
    }

    public String mask(String value) { return empty(value) ? "NOT-SET" : "SET"; }
}
