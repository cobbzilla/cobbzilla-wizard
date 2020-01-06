package org.cobbzilla.wizard.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.utils.URIBuilder;
import org.cobbzilla.util.collection.SingletonList;
import org.cobbzilla.util.daemon.ErrorApi;
import org.cobbzilla.util.json.JsonUtil;
import org.cobbzilla.util.network.NetworkUtil;
import org.cobbzilla.util.network.PortPicker;
import org.cobbzilla.util.reflect.ReflectionUtil;
import org.cobbzilla.util.security.bcrypt.BCryptUtil;
import org.cobbzilla.wizard.model.entityconfig.EntityConfigSource;
import org.cobbzilla.wizard.model.entityconfig.EntityConfigValidator;
import org.cobbzilla.wizard.server.config.*;
import org.cobbzilla.wizard.server.config.factory.ConfigurationSource;
import org.cobbzilla.wizard.server.config.factory.StreamConfigurationSource;
import org.cobbzilla.wizard.server.handler.StaticAssetHandler;
import org.cobbzilla.wizard.validation.Validator;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.glassfish.grizzly.http.server.*;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpContainer;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.message.internal.StreamingOutputProvider;
import org.glassfish.jersey.server.ContainerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Service;

import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.network.NetworkUtil.IPv4_ALL_ADDRS;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;
import static org.cobbzilla.util.reflect.ReflectionUtil.forNames;
import static org.cobbzilla.util.string.StringUtil.EMPTY_ARRAY;
import static org.cobbzilla.util.system.Sleep.sleep;

@NoArgsConstructor @Slf4j
public abstract class RestServerBase<C extends RestServerConfiguration> implements RestServer<C> {

    public RestServerBase (RestServer<C> other) {
        copy(this, other, new String[]{"httpServer", "configuration", "applicationContext"});
    }

    @Getter @Setter private volatile static ErrorApi errorApi;

    @Getter private HttpServer httpServer;
    @Getter @Setter private C configuration;

    @Getter private final Map<String, RestServerLifecycleListener<C>> listeners = new LinkedHashMap<>();

    @Override public void addLifecycleListener(RestServerLifecycleListener<C> listener) {
        synchronized (listeners) { listeners.put(listenerKey(listener), listener); }
    }
    @Override public void removeLifecycleListener(RestServerLifecycleListener<C> listener) {
        synchronized (listeners) { listeners.remove(listenerKey(listener)); }
    }
    @Override public Collection<RestServerLifecycleListener<C>> removeAllLifecycleListeners() {
        final List<RestServerLifecycleListener<C>> saved = new ArrayList<>();
        synchronized (listeners) {
            saved.addAll(listeners.values());
            listeners.clear();
        }
        return saved;
    }

    protected String listenerKey(RestServerLifecycleListener<C> listener) { return listener.getClass().getName()+listener.hashCode(); }

    private ConfigurableApplicationContext applicationContext;
    public ApplicationContext getApplicationContext () { return applicationContext; }

    private boolean hasPort () {
        return configuration != null && configuration.getHttp() != null && configuration.getHttp().getPort() != 0;
    }

    private void verifyPort() {
        if (!hasPort()) {
            int port = PortPicker.pickOrDie();
            log.info("No port defined, picked: "+port);
            configuration.getHttp().setPort(port);
        }
    }

    public URI getBaseUri() { return buildURI(getListenAddress()); }

    protected String getListenAddress() { return IPv4_ALL_ADDRS; }

    public String getPrimaryListenAddress() {
        String addr = getListenAddress();
        if (!addr.equals(IPv4_ALL_ADDRS)) return addr;
        addr = NetworkUtil.getFirstPublicIpv4();
        if (!empty(addr)) return addr;
        addr = NetworkUtil.getFirstEthernetIpv4();
        if (!empty(addr)) return addr;
        return die("getPrimaryListenAddress: could not determine address");
    }

    @Override public String getClientUri() {
        verifyPort();
        return buildURI(getPrimaryListenAddress()).toString();
    }

    protected URI buildURI(String host) {
        verifyPort();
        final HttpConfiguration httpConfiguration = configuration.getHttp();
        try {
            return new URIBuilder()
                    .setScheme("http")
                    .setHost(host)
                    .setPort(httpConfiguration.getPort())
                    .setPath(httpConfiguration.getBaseUri())
                    .build();
        } catch (URISyntaxException e) {
            return die("buildURI:" +e, e);
        }
    }

    public synchronized HttpServer startServer() throws IOException {

        for (RestServerLifecycleListener<C> listener : listeners.values()) listener.beforeStart(this);

        final String serverName = configuration.getServerName();
        httpServer = buildServer(serverName);

        // fire it up
        log.info("starting "+serverName+"...");
        httpServer.start();
        // httpServer = GrizzlyServerFactory.createHttpServer(getBaseUri(), rc, factory);
        log.info(serverName+" started.");

        // try to use an EntityConfigValidator if we have an EntityConfigSource
        try {
            final EntityConfigSource entityConfigSource = getBean(EntityConfigSource.class);
            if (entityConfigSource != null) {
                configuration.setValidator(new EntityConfigValidator(entityConfigSource));
            }
        } catch (Exception e) {
            log.warn("startServer: no EntityConfigSource found: "+e.getMessage());
        }
        for (RestServerLifecycleListener<C> listener : listeners.values()) listener.onStart(this);
        return httpServer;
    }

    @Override public boolean isRunning() { return httpServer != null && httpServer.isStarted(); }

    @Getter(lazy=true) private final Server jetty = initJetty();
    private Server initJetty() {
        final HttpConfiguration httpConfiguration = getConfiguration().getHttp();
        if (!httpConfiguration.hasWebPort()) {
            httpConfiguration.setWebPort(PortPicker.pickOrDie());
        }
        return new Server(httpConfiguration.getWebPort());
    }

    public HttpServer buildServer(String serverName) throws IOException {

        final ResourceConfig rc = getJerseyResourceConfig(configuration.getJersey());

        BCryptUtil.setBcryptRounds(configuration.getBcryptRounds());

        applicationContext = buildSpringApplicationContext();
        configuration.setApplicationContext(applicationContext);

        // set the IoC factory
        rc.property("contextConfig", applicationContext);

        // pick a port
        if (!configuration.getHttp().hasPort()) {
            configuration.getHttp().setPort(PortPicker.pick());
        }

        final HttpServer httpServer = new HttpServer();
        final NetworkListener listener = new NetworkListener("grizzly-"+serverName, getListenAddress(), configuration.getHttp().getPort());
        httpServer.addListener(listener);

        final ServerConfiguration serverConfig = httpServer.getServerConfiguration();

        // add handlers -- first the optional webapps
        if (configuration.hasWebapps()) {
            final ContextHandlerCollection contexts = new ContextHandlerCollection();
            final List<Handler> handlers = new ArrayList<>();
            for (WebappConfiguration config : configuration.getWebapps()) {
                handlers.add(config.deploy(applicationContext));
            }
            try {
                contexts.setHandlers((Handler[]) handlers.toArray());
                final Server jetty = getJetty();
                jetty.setHandler(contexts);
                jetty.setStopAtShutdown(true);
                jetty.start();
            } catch (Exception e) {
                return die("buildServer: error starting Jetty to run webapps: "+e, e);
            }
        }

        // then the REST/Jersey handler
        final HttpHandler processor = ContainerFactory.createContainer(GrizzlyHttpContainer.class, rc);
        final String restBase = configuration.getHttp().getBaseUri();
        serverConfig.addHttpHandler(processor, restBase);

        // then optional static asset handler
        if (configuration.hasStaticAssets()) {
            final StaticHttpConfiguration staticAssets = configuration.getStaticAssets();
            final String staticBase = staticAssets.getBaseUri();
            if (staticBase == null || staticBase.trim().length() == 0 || staticBase.trim().equals(restBase)) {
                return die("buildServer: staticAssetBaseUri not defined, or is same as restServerBaseUri");
            }
            final StaticAssetHandler staticHandler = new StaticAssetHandler(staticAssets, getClass().getClassLoader());
            serverConfig.addHttpHandler(staticHandler, staticBase);
        }

        // then any other optional additional http handlers
        if (configuration.hasHandlers()) {
            for (HttpHandlerConfiguration config : configuration.getHandlers()) {

                // which bean will handle this?
                final Object bean = getBean(config.getBean());
                if (!(bean instanceof HttpHandler)) {
                    log.warn("buildServer: bean is not an instance of HttpHandler: "+config.getBean());
                    continue;
                }
                final HttpHandler handler = (HttpHandler) bean;

                final HttpHandlerRegistration handlerRegistration = HttpHandlerRegistration.builder()
                        .contextPath(config.contextPath())
                        .urlPattern("/*")
                        .build();

                serverConfig.addHttpHandler(handler, handlerRegistration);
            }
        }

        return httpServer;
    }

    protected ResourceConfig getJerseyResourceConfig(JerseyConfiguration jerseyConfiguration) {

        final ResourceConfig rc = new ResourceConfig(findClassesWithAnnotation(jerseyConfiguration.getResourcePackages(), Service.class));

        if (jerseyConfiguration.hasRequestFilters()) {
            for (Class c : forNames(jerseyConfiguration.getRequestFilters())) rc.register(c);
        }
        if (jerseyConfiguration.hasResponseFilters()) {
            for (Class c : forNames(jerseyConfiguration.getResponseFilters())) rc.register(c);
        }
        if (jerseyConfiguration.hasProviderPackages()) {
            for (Class c : findClassesWithAnnotation(jerseyConfiguration.getProviderPackages(), Provider.class)) rc.register(c);
        }

        configuration.setValidator(new Validator());
        rc.register(new JacksonMessageBodyProvider(getObjectMapper(), configuration.getValidator()));
        rc.register(new StreamingOutputProvider());
        rc.register(MultiPartFeature.class);
        // rc.register(new StringProvider());
        return rc;
    }

    private Class<?>[] findClassesWithAnnotation(String[] packages, Class<? extends Annotation> annotation) {
        final ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(annotation));
        final List classes = new ArrayList();
        for (String pkg : packages) {
            for (BeanDefinition def : scanner.findCandidateComponents(pkg)) {
                classes.add(ReflectionUtil.forName(def.getBeanClassName()));
            }
        }
        return (Class<?>[]) classes.toArray(new Class<?>[classes.size()]);
    }

    protected ObjectMapper getObjectMapper() { return JsonUtil.NOTNULL_MAPPER; }

    @Override public ConfigurableApplicationContext buildSpringApplicationContext() {
        return buildSpringApplicationContext(new ApplicationContextConfig<>(configuration));
    }

    @Override public ConfigurableApplicationContext buildSpringApplicationContext(final ApplicationContextConfig ctxConfig) {

        final RestServer server = this;

        // Create a special factory that will always correctly resolve this specific configuration
        final DefaultListableBeanFactory factory = new RestServerListableBeanFactory(server, ctxConfig);

        // Create a special context that uses the above factory
        final ClassPathXmlApplicationContext applicationContext = new RestServerClassPathXmlApplicationContext(factory);

        // create the full context, with the config bean now "predefined"
        applicationContext.setConfigLocation(ctxConfig.getSpringContextPath());
        applicationContext.refresh();
        return applicationContext;
    }

    protected long shutdownTimeout() { return TimeUnit.SECONDS.toMillis(5); }

    public synchronized void stopServer() {
        // stop jetty first, if running
        if (configuration.hasWebapps()) {
            try {
                getJetty().stop();
            } catch (Exception e) {
                log.warn("stopServer: error stopping jetty: "+e, e);
            }
        }
        if (httpServer != null && httpServer.isStarted()) {
            log.info("stopServer: stopping " + configuration.getServerName() + "...");
            for (RestServerLifecycleListener<C> listener : listeners.values()) listener.beforeStop(this);
            try {
                httpServer.shutdownNow();
            } finally {
                long start = realNow();
                while (httpServer.isStarted() && realNow() - start < shutdownTimeout()) {
                    sleep(100);
                }
                if (httpServer.isStarted()) log.warn("stopServer: server did not stop, running onStop for "+listeners.size()+" handlers anyway");
                for (RestServerLifecycleListener<C> listener : listeners.values()) listener.onStop(this);
            }
            log.info("stopServer: " + configuration.getServerName() + " stopped.");
        } else {
            log.info("stopServer: httpServer not running");
        }
    }

    private static final Object mainThreadLock = new Object();

    public static <S extends RestServerBase<C>, C extends RestServerConfiguration> S
    main(Class<S> mainClass,
         ConfigurationSource configSource) throws Exception {
        return main(EMPTY_ARRAY, mainClass, null, configSource);
    }

    public static <S extends RestServerBase<C>, C extends RestServerConfiguration> S
    main(Class<S> mainClass,
         ConfigurationSource configSource,
         Map<String, String> env) throws Exception {
        return main(EMPTY_ARRAY, mainClass, Collections.emptyList(), configSource, env);
    }

    public static <S extends RestServerBase<C>, C extends RestServerConfiguration> S
    main(String[] args,
         Class<S> mainClass,
         ConfigurationSource configSource) throws Exception {
        return main(args, mainClass, null, configSource);
    }

    public static <S extends RestServerBase<C>, C extends RestServerConfiguration> S
    main(Class<S> mainClass,
         final RestServerLifecycleListener listener,
         ConfigurationSource configSource) throws Exception {
        return main(EMPTY_ARRAY, mainClass, listener, configSource);
    }

    public static <S extends RestServerBase<C>, C extends RestServerConfiguration> S
    main(Class<S> mainClass,
         final RestServerLifecycleListener listener,
         ConfigurationSource configSource,
         Map<String, String> env) throws Exception {
        return main(EMPTY_ARRAY, mainClass, listener, configSource, env);
    }

    public static <S extends RestServer<C>, C extends RestServerConfiguration> S
    main(String[] args, Class<S> mainClass,
         final RestServerLifecycleListener listener,
         ConfigurationSource configSource) throws Exception {
        return main(args, mainClass, listener, configSource, null);
    }

    public static <S extends RestServer<C>, C extends RestServerConfiguration> S
    main(String[] args, Class<S> mainClass,
         final RestServerLifecycleListener listener,
         ConfigurationSource configSource,
         Map<String, String> env) throws Exception {
        return main(args, mainClass, new SingletonList<>(listener), configSource, env);
    }

    public static <S extends RestServer<C>, C extends RestServerConfiguration> S
    main(Class<S> mainClass,
         List<RestServerLifecycleListener> listeners,
         ConfigurationSource configSource,
         Map<String, String> env) throws Exception {
        return main(EMPTY_ARRAY, mainClass, listeners, configSource, env);
    }

    public static <S extends RestServer<C>, C extends RestServerConfiguration> S
    main(String[] args, Class<S> mainClass,
         List<RestServerLifecycleListener> listeners,
         ConfigurationSource configSource,
         Map<String, String> env) throws Exception {

        final Thread mainThread = Thread.currentThread();

        // Ignore "server" argument if it is the first arg
        final List<String> argList = Arrays.asList(args);
        if (argList.size() >= 1 && argList.get(0).equals("server")) {
            argList.remove(0);
        }

        final RestServerHarness<C, S> serverHarness = new RestServerHarness<>(mainClass);
        serverHarness.setConfigurationSource(configSource);
        serverHarness.init(env != null ? env : System.getenv());

        final S server = serverHarness.getServer();
        if (listeners != null) {
            for (RestServerLifecycleListener listener : listeners) server.addLifecycleListener(listener);
        }
        server.startServer();

        final String serverName = server.getConfiguration().getServerName();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("stopping "+serverName);
            server.stopServer();
            synchronized (mainThreadLock) {
                mainThread.interrupt();
                mainThreadLock.notify();
            }
        }));

        log.info(serverName+" running, base URI is " + server.getBaseUri().toString());
        try {
            synchronized (mainThreadLock) { mainThreadLock.wait(); }
        } catch (InterruptedException e) {
            log.info(serverName+" server thread interrupted, stopping server...");
        }

        return server;
    }

    public static ConfigurationSource getStreamConfigurationSource(Class clazz, String path) {
        return StreamConfigurationSource.fromResource(clazz, path);
    }

    public <T> T getBean(String beanName) { return (T) getApplicationContext().getBean(beanName); }

    public <T> T getBean(Class<T> beanClass) { return getApplicationContext().getBean(beanClass); }

    /**
     * Send error to external error reporting system, like Errbit or Airbrake
     * @param s the message
     */
    public static void reportError(String s) {
        if (errorApi != null) {
            try {
                errorApi.report(s);
            } catch (Exception e2) {
                log.error("report: error reporting exception ("+s+"): "+e2, e2);
            }
        }
    }

    /**
     * Send error to external error reporting system, like Errbit or Airbrake
     * @param e the exception
     */
    public static void reportError(Exception e) {
        if (errorApi != null) {
            try {
                errorApi.report(e);
            } catch (Exception e2) {
                log.error("report: error reporting exception ("+e+"): "+e2, e2);
            }
        }
    }

    public static void reportError(String s, Exception e) {
        if (errorApi != null) {
            try {
                errorApi.report(s, e);
            } catch (Exception e2) {
                log.error("report: error reporting exception ("+s+", "+e+"): "+e2, e2);
            }
        }
    }

    @Override public String getDefaultTmpdirEnvVar() { return null; }

}
