package org.cobbzilla.wizard.server.config;

import lombok.Getter;
import lombok.Setter;

public class JerseyConfiguration {

    @Getter @Setter private String[] resourcePackages;

    @Getter @Setter private String[] providerPackages;
    public boolean hasProviderPackages () { return providerPackages != null && providerPackages.length > 0; }

    @Getter @Setter private String[] requestFilters;
    public boolean hasRequestFilters() { return requestFilters != null && requestFilters.length > 0; }

    @Getter @Setter private String[] responseFilters;
    public boolean hasResponseFilters() { return responseFilters != null && responseFilters.length > 0; }

}
