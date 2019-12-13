package org.cobbzilla.wizard.server.config;

import lombok.Getter;
import lombok.Setter;

import static org.apache.commons.lang3.StringUtils.chop;

public class HttpHandlerConfiguration {

    @Getter @Setter private String uri;
    @Getter @Setter private String bean;

    public String contextPath() { return uri.endsWith("/") ? chop(uri) : uri; }

}
