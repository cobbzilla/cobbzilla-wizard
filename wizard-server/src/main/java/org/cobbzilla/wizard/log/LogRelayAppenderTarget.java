package org.cobbzilla.wizard.log;

import java.util.Map;

public interface LogRelayAppenderTarget {

    boolean init (Map<String, String> params);
    void relay (String line);

}
