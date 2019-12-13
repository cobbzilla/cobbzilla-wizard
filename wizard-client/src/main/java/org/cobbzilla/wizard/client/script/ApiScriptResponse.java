package org.cobbzilla.wizard.client.script;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Arrays;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.http.HttpStatusCodes.OK;
import static org.cobbzilla.util.time.TimeUtil.parseDuration;

@Accessors(chain=true)
public class ApiScriptResponse {

    @Getter @Setter private int status = OK;
    @Getter @Setter private int[] okStatus;

    @Getter @Setter private String session;
    public boolean hasSession() { return !empty(session); }

    @Getter @Setter private String sessionName;
    public boolean hasSessionName() { return !empty(sessionName); }

    @Getter @Setter private String store;
    public boolean hasStore() { return !empty(store); }

    @Getter @Setter private String type;
    public boolean hasType() { return !empty(type); }

    @Getter @Setter private ApiScriptResponseCheck[] check;
    public boolean hasChecks() { return !empty(check); }

    @Getter @Setter private boolean raw;

    @Getter @Setter private String delay;
    public boolean hasDelay () { return !empty(delay); }
    @JsonIgnore public long getDelayMillis () { return parseDuration(delay); }

    public boolean statusOk(int status) {
        if (okStatus != null) return Arrays.stream(okStatus).anyMatch(s -> s == status);
        return status == this.status;
    }

}
