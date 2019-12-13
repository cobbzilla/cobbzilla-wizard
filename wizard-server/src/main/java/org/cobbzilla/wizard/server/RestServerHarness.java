package org.cobbzilla.wizard.server;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.io.FileUtil;
import org.cobbzilla.wizard.server.config.RestServerConfiguration;
import org.cobbzilla.wizard.server.config.factory.ConfigurationSource;
import org.cobbzilla.wizard.server.config.factory.RestServerConfigurationFactory;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.cobbzilla.util.io.FileUtil.abs;
import static org.cobbzilla.util.io.FileUtil.getDefaultTempDir;
import static org.cobbzilla.util.io.FileUtil.mkdirOrDie;
import static org.cobbzilla.util.reflect.ReflectionUtil.*;
import static org.cobbzilla.util.string.StringUtil.camelCaseToSnakeCase;

@Slf4j
public class RestServerHarness<C extends RestServerConfiguration, S extends RestServer<C>> {

    @Getter @Setter private Class<S> restServerClass;
    @Getter @Setter private ConfigurationSource configurationSource = null;
    @Getter private S server = null;
    @Getter @Setter private C configuration = null;

    private final AtomicBoolean started = new AtomicBoolean(false);

    public RestServerHarness(Class<S> restServerClass) { this.restServerClass = restServerClass; }

    public synchronized void startServer() throws Exception { startServer(null); }

    public synchronized void startServer(Map<String, String> env) throws Exception {
        if (!started.getAndSet(true)) {
            if (server == null) init(env);
            server.startServer();
        } else {
            log.warn("startServer: server already started");
        }
    }

    public synchronized void init(Map<String, String> env) {
        if (server == null) {
            server = instantiate(getRestServerClass());

            final Class<C> configurationClass = getTypeParameter(getRestServerClass(), RestServerConfiguration.class);
            final RestServerConfigurationFactory<C> factory = new RestServerConfigurationFactory<>(configurationClass);
            if (configuration == null) {
                configuration = filterConfiguration(factory.build(configurationSource, env));
                configuration.setEnvironment(env);
                configuration.setTmpdir(getTmpDir(server, env));
            }
            server.setConfiguration(configuration);
            configuration.setServer(server);
            log.info("starting " + configuration.getServerName() + ": " + server.getClass().getName() + " with config: " + configuration);
        }
    }

    private List<RestServerConfigurationFilter> configurationFilters = new ArrayList<>();
    public void addConfigurationFilter(RestServerConfigurationFilter filter) {
        configurationFilters.add(filter);
    }

    protected C filterConfiguration(C configuration) {
        for (RestServerConfigurationFilter filter : configurationFilters) {
            configuration = (C) filter.filterConfiguration(configuration);
        }
        return configuration;
    }

    public synchronized void stopServer () throws Exception {
        if (server != null) {
            server.stopServer();
            server = null;
        }
    }

    public String getDefaultTmpDirName(S server) { return camelCaseToSnakeCase(server.getClass().getSimpleName()).toUpperCase()+"_TMPDIR"; }

    public File getTmpDir(S server, Map<String, String> env) {
        String defaultTmpdirEnvVar = server.getDefaultTmpdirEnvVar();
        if (defaultTmpdirEnvVar == null) {
            defaultTmpdirEnvVar = getDefaultTmpDirName(server);
        }
        final String tmpdirValue = env.get(defaultTmpdirEnvVar);
        final File tmpdir;
        if (tmpdirValue != null) {
            tmpdir = mkdirOrDie ( new File(tmpdirValue) );
            FileUtil.defaultTempDir = abs(tmpdir);
        } else {
            tmpdir = getDefaultTempDir();
            log.warn("No "+defaultTmpdirEnvVar+" environment variable found, using defaultTempDir="+abs(tmpdir));
        }
        return tmpdir;
    }

    public ConfigurableApplicationContext springServer(ConfigurationSource configurationSource,
                                                       Map<String, String> env) throws Exception {
        this.configurationSource = configurationSource;
        init(env == null || env.isEmpty() ? System.getenv() : env);
        return server.buildSpringApplicationContext();
    }
}
