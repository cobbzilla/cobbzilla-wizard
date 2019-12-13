package org.cobbzilla.wizard.model;

import javax.persistence.MappedSuperclass;

import static java.util.UUID.randomUUID;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.daemon.ZillaRuntime.hexnow;

@MappedSuperclass
public class StrongIdentifiableBase extends IdentifiableBase {

    @Override public void initUuid() { setUuid(newStrongUuid()); }

    public static String newStrongUuid() { return randomUUID().toString() + "-" + hexnow(); }

    public static boolean isStrongUuid(String maybeUuid) {
        // must be non-empty and contain at least 40 hex chars
        // todo: fix this check
        return !empty(maybeUuid)
                && maybeUuid.length() >= 40
                && maybeUuid.replaceAll("[\\WA-Fa-f\\d]+", "").length() == 0;
    }
}
