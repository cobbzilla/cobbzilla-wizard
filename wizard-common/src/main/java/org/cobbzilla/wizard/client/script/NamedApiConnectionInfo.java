package org.cobbzilla.wizard.client.script;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.http.ApiConnectionInfo;

@NoArgsConstructor @Accessors(chain=true)
public class NamedApiConnectionInfo extends ApiConnectionInfo {

    @Getter @Setter private String name;
    public boolean hasName() { return name != null; }

}
