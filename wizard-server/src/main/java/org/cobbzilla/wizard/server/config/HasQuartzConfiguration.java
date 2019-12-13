package org.cobbzilla.wizard.server.config;

import java.util.Properties;

public interface HasQuartzConfiguration {

    Properties getQuartz();
    boolean isRunScheduler();

}
