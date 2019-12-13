package org.cobbzilla.wizard.server.config;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections4.iterators.IteratorEnumeration;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.webapp.WebAppContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import javax.servlet.Servlet;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.reflect.ReflectionUtil.instantiate;
import static org.cobbzilla.wizard.util.SpringUtil.autowire;

public class WebappConfiguration {

    @Getter @Setter private String name;
    @Getter @Setter private String path;
    @Getter @Setter private ServletConfiguration[] servlets;

    public Handler deploy(ConfigurableApplicationContext applicationContext) {
        final WebAppContext context = new WebAppContext();
        final ContextHandler contextHandler = new ContextHandler(path);
        context.setDisplayName(name);
        for (ServletConfiguration servletConfiguration : getServlets()) {
            final ServletHolder initializingHolder = (ServletHolder) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{ServletHolder.class},
                    new InitializingHandler(servletConfiguration, applicationContext));
            context.addServlet(initializingHolder, servletConfiguration.getMapping());
        }
        contextHandler.setHandler(context);
        return contextHandler;
    }

    private class InitializingHandler implements InvocationHandler {

        private ServletConfiguration servletConfiguration;
        private Servlet servlet;

        public InitializingHandler(ServletConfiguration servletConfiguration, ApplicationContext applicationContext) {
            this.servletConfiguration = servletConfiguration;
            try {
                this.servlet = instantiate(servletConfiguration.getClassName());
                autowire(applicationContext, servlet);
            } catch (Exception e) {
                die("InitializingHandler: "+e);
            }
        }

        @Override public Object invoke(Object o, Method m, Object[] args) throws Throwable {
            switch (m.getName()) {
                case "getDisplayName": return servletConfiguration.getName();
                case "getAsyncSupported": return servletConfiguration.isAsyncSupported();
                case "getInitParameters": return servletConfiguration.getInitParams();
                case "getInitParameterNames": return new IteratorEnumeration(servletConfiguration.getInitParams().values().iterator());
                case "getServlet": return servlet;
            }
            return m.invoke(o, args);
        }
    }

}
