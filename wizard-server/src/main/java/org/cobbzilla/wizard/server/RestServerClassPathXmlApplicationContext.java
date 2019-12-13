package org.cobbzilla.wizard.server;

import lombok.AllArgsConstructor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

@AllArgsConstructor
class RestServerClassPathXmlApplicationContext extends ClassPathXmlApplicationContext {

    private final DefaultListableBeanFactory factory;

    @Override protected DefaultListableBeanFactory createBeanFactory() { return factory; }

}
