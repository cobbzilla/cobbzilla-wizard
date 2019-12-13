package org.cobbzilla.wizard.server;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.wizard.server.config.RestServerConfiguration;

@NoArgsConstructor @AllArgsConstructor @Accessors(chain=true)
public class ApplicationContextConfig<C extends RestServerConfiguration> {

    @Getter @Setter private C config;
    @Setter private String springContextPath = "spring.xml";

    public String getSpringContextPath() {
        return springContextPath.startsWith("classpath:/") ? springContextPath : "classpath:/" + springContextPath;
    }

    @Getter @Setter private CustomBeanResolver[] resolvers = null;
    public boolean hasResolvers() { return resolvers != null && resolvers.length > 0; }

    public ApplicationContextConfig(C configuration) {
        this.config = configuration;
        this.springContextPath = configuration.getSpringContextPath();
    }

}
