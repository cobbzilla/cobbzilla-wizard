package org.cobbzilla.wizard.server.config;

import lombok.Getter;
import lombok.Setter;

import java.net.URI;
import java.net.URISyntaxException;

public class HttpConfiguration {

    @Getter @Setter private Integer port = null;
    public boolean hasPort() { return port != null && port != 0; }

    @Getter @Setter private Integer webPort = null;
    public boolean hasWebPort() { return webPort != null && webPort != 0; }

    @Getter @Setter private String baseUri;

    public String getHost () throws URISyntaxException {
        return new URI(baseUri).getHost();
    }

    @Getter @Setter private Integer selectorThreads;
    public boolean hasSelectorThreads () { return selectorThreads != null; }

    @Getter @Setter private Integer workerThreads;
    public boolean hasWorkerThreads () { return workerThreads != null; }

    @Getter @Setter private boolean exitOnOutOfMemoryError = true;
}
