package org.cobbzilla.wizard.model;

import lombok.*;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper=false)
public class ApiToken {

    // If this changes, or becomes configurable, refactor existing app store clients
    // to refresh their tokens at appropriate intervals
    public static final int EXPIRATION_SECONDS = (int) TimeUnit.DAYS.toSeconds(1);

    @Getter @Setter private String token;

    public ApiToken init () { token = UUID.randomUUID().toString(); return this; }

}
