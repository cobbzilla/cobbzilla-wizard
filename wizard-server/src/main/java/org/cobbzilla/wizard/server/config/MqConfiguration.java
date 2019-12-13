package org.cobbzilla.wizard.server.config;

import lombok.Getter;
import lombok.Setter;

import java.util.Properties;

public class MqConfiguration {

    @Getter @Setter private String clientClass;
    @Getter @Setter private String queuePrefix;
    @Getter @Setter private String queueName;
    @Getter @Setter private String errorQueueName;
    @Getter @Setter private Properties properties;

}
