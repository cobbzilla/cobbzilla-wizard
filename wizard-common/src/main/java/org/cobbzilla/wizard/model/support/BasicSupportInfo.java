package org.cobbzilla.wizard.model.support;

import lombok.Getter;
import lombok.Setter;
import org.cobbzilla.wizard.model.entityconfig.EntityFieldType;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECField;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

public class BasicSupportInfo {

    @ECField(type=EntityFieldType.email)
    @Getter @Setter private String email;
    public boolean getHasEmail () { return !empty(email); }

    @ECField(type=EntityFieldType.http_url)
    @Getter @Setter private String site;
    public boolean getHasSite () { return !empty(site); }

    public boolean getHasInfo() { return !empty(email) || !empty(site); }
    public boolean getHasEmailAndSite() { return !empty(email) && !empty(site); }

}
