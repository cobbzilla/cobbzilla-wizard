package org.cobbzilla.wizard.server;

import org.springframework.beans.BeansException;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.util.Set;

class RestServerListableBeanFactory extends DefaultListableBeanFactory {

    private final RestServer server;
    private final ApplicationContextConfig ctxConfig;

    public RestServerListableBeanFactory(RestServer server, ApplicationContextConfig ctxConfig) {
        this.server = server;
        this.ctxConfig = ctxConfig;
    }

    @Override public Object doResolveDependency(DependencyDescriptor descriptor,
                                                String beanName,
                                                Set<String> autowiredBeanNames,
                                                TypeConverter typeConverter) throws BeansException {
        // it's the configuration
        if (descriptor.getDependencyType().isAssignableFrom(ctxConfig.getConfig().getClass())) return ctxConfig.getConfig();

        // it's the server
        if (RestServer.class.isAssignableFrom(descriptor.getDependencyType())) return server;

        // maybe it can be resolved by a custom resolver
        if (ctxConfig.hasResolvers()) {
            for (CustomBeanResolver resolver : ctxConfig.getResolvers()) {
                Object resolved = resolver.resolve(this, descriptor, beanName, autowiredBeanNames, typeConverter);
                if (resolved != null) return resolved;
            }
        }

        // normal dependency resolution
        return super.doResolveDependency(descriptor, beanName, autowiredBeanNames, typeConverter);
    }

}
