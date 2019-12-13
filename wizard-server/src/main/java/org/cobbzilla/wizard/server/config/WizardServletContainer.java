package org.cobbzilla.wizard.server.config;

import org.springframework.context.ConfigurableApplicationContext;

public interface WizardServletContainer {

    void deploy(WebappConfiguration config, ConfigurableApplicationContext applicationContext);

}
