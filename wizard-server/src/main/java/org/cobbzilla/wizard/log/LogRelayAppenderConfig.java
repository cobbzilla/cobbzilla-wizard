package org.cobbzilla.wizard.log;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

public class LogRelayAppenderConfig {

    @Getter @Setter private String relayTo;
    @Getter @Setter private Map<String, String> params;

}
