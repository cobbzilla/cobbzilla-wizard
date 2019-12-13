package org.cobbzilla.wizard.main;

import org.cobbzilla.util.main.BaseMain;
import org.cobbzilla.util.main.BaseMainOptions;
import org.cobbzilla.wizard.util.RestResponse;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

public abstract class MainBase<OPT extends BaseMainOptions> extends BaseMain<OPT> {

    protected static void out (RestResponse response) {
        out((response.isSuccess() || response.isInvalid()) && !empty(response.json) ? response.json : response.toString());
    }

}
