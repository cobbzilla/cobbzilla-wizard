package org.cobbzilla.wizard.server.config;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

public class JerseyConfiguration {

    @Getter @Setter private String[] resourcePackages;

    @Getter @Setter private String[] providerPackages;
    public boolean hasProviderPackages () { return !empty(providerPackages); }

    @Getter @Setter private String[] requestFilters;
    public boolean hasRequestFilters() { return !empty(requestFilters); }

    @Getter @Setter private String[] responseFilters;
    public boolean hasResponseFilters() { return !empty(responseFilters); }

    @Getter @Setter private Map<String, Object> serverProperties;
    public boolean hasServerProperties () { return !empty(serverProperties); }

}
