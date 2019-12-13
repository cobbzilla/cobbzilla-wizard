package org.cobbzilla.wizard.server.config;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

public class ServletConfiguration {

    @Getter @Setter private String className;
    @Getter @Setter private String name;
    @Getter @Setter private String mapping;
    @Getter @Setter private boolean asyncSupported = false;
    @Getter @Setter private Map<String, String> initParams = new HashMap<>();

}
