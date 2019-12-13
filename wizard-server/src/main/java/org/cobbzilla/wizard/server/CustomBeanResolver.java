package org.cobbzilla.wizard.server;

import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.util.Set;

public interface CustomBeanResolver {

    Object resolve(DefaultListableBeanFactory defaultListableBeanFactory,
                   DependencyDescriptor descriptor,
                   String beanName,
                   Set<String> autowiredBeanNames,
                   TypeConverter typeConverter);

}
