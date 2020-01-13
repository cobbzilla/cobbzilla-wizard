package org.cobbzilla.wizardtest.resources;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.http.HttpStatusCodes;
import org.cobbzilla.util.json.JsonUtil;
import org.cobbzilla.wizard.client.ApiClientBase;
import org.cobbzilla.wizard.client.script.ApiRunner;
import org.cobbzilla.wizard.client.script.ApiRunnerListenerBase;
import org.cobbzilla.wizard.server.RestServer;
import org.cobbzilla.wizard.server.RestServerConfigurationFilter;
import org.cobbzilla.wizard.server.RestServerHarness;
import org.cobbzilla.wizard.server.RestServerLifecycleListener;
import org.cobbzilla.wizard.server.config.*;
import org.cobbzilla.wizard.server.config.factory.ConfigurationSource;
import org.cobbzilla.wizard.server.config.factory.StreamConfigurationSource;
import org.cobbzilla.wizard.server.listener.DbPoolShutdownListener;
import org.cobbzilla.wizard.util.RestResponse;
import org.cobbzilla.wizard.validation.ConstraintViolationBean;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.io.StreamUtil.loadResourceAsStringOrDie;
import static org.cobbzilla.util.io.StreamUtil.stream2string;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.reflect.ReflectionUtil.getFirstTypeParam;
import static org.cobbzilla.util.string.StringUtil.camelCaseToSnakeCase;
import static org.cobbzilla.util.string.StringUtil.truncate;
import static org.cobbzilla.util.system.Sleep.sleep;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.quartz.impl.StdSchedulerFactory.*;
import static org.quartz.utils.PoolingConnectionProvider.DB_URL;

@FixMethodOrder(MethodSorters.NAME_ASCENDING) @Slf4j
public abstract class AbstractResourceIT<C extends PgRestServerConfiguration, S extends RestServer<C>>
        implements RestServerLifecycleListener<C>, RestServerConfigurationFilter<C> {

    public static final String QUARTZ_SQL_COMMANDS = stream2string("seed/quartz.sql");

    @Getter private final ApiClientBase api = new BasicTestApiClient(this);

    public void setToken(String sessionId) { getApi().setToken(sessionId); }
    public void pushToken(String sessionId) { getApi().pushToken(sessionId); }
    public void setCaptureHeaders(boolean capture) { getApi().setCaptureHeaders(capture); }
    public void logout() { getApi().logout(); }

    public <T> T get(String url, Class<T> clazz) throws Exception { return json(getApi().get(url).json, clazz); }
    public RestResponse get(String url) throws Exception { return getApi().get(url); }
    public RestResponse doGet(String url) throws Exception { return getApi().doGet(url); }

    public RestResponse put(String url, String json) throws Exception { return getApi().put(url, json); }
    public <T> T put(String url, T o) throws Exception { return getApi().put(url, o); }
    public <T> T put(String path, Object request, Class<T> responseClass) throws Exception { return getApi().put(path, request, responseClass); }
    public RestResponse doPut(String uri, String json) throws Exception { return getApi().doPut(uri, json); }

    public RestResponse post(String url, String json) throws Exception { return getApi().post(url, json); }
    public <T> T post(String url, T o) throws Exception { return getApi().post(url, o); }
    public <T> T post(String path, Object request, Class<T> responseClass) throws Exception { return getApi().post(path, request, responseClass); }
    public RestResponse doPost(String uri, String json) throws Exception { return getApi().doPost(uri, json); }

    public RestResponse delete(String url) throws Exception { return getApi().delete(url); }
    public RestResponse doDelete(String url) throws Exception { return getApi().doDelete(url); }

    protected abstract ConfigurationSource getConfigurationSource();
    protected ConfigurationSource getConfigurationSource(String path) {
        return StreamConfigurationSource.fromResource(getClass(), path);
    }

    protected Class<? extends S> getRestServerClass() { return getFirstTypeParam(getClass(), RestServer.class); }

    public interface PreRestoreTask { File handle (File dbFile); }

    protected List<PreRestoreTask> preRestoreTasks = new ArrayList<>();
    protected List<Runnable> postRestoreTasks = new ArrayList<>();

    public String getServerCacheKey() { return getClass().getName(); }

    @Override public C filterConfiguration(final C configuration) {

        final boolean hasDb = configuration instanceof HasDatabaseConfiguration;
        final boolean hasQuartz = configuration instanceof HasQuartzConfiguration;
        if (hasDb) {
            final DatabaseConfiguration database = ((HasDatabaseConfiguration) configuration).getDatabase();
            if (useTestSpecificDatabase()) {
                // we'll use this to randomize the name of our database and server
                final String rand = getDatabaseNameSuffix();
                log.debug("filterConfiguration: using random token "+rand+" for test "+getServerCacheKey());
                final String serverName = configuration.getServerName() + "-" + rand;
                configuration.setServerName(serverName);
                database.getPool().setName("pool_"+serverName);

                String url = database.getUrl();
                int lastSlash = url.lastIndexOf('/');
                if (lastSlash == -1 || lastSlash == url.length() - 1) {
                    log.warn("filterConfiguration: couldn't understand url: " + url + ", leaving as is");
                    return configuration;
                }
                final String dbName = getTempDbNamePrefix(url) + "_" + rand;
                database.setUrl(url.substring(0, lastSlash) + "/" + dbName);

                if (hasQuartz) {
                    final Properties quartz = ((HasQuartzConfiguration) configuration).getQuartz();
                    if (quartz != null) {
                        // rename scheduler
                        final String newSchedName = quartz.getProperty(PROP_SCHED_INSTANCE_NAME) + "_" + rand;
                        quartz.setProperty(PROP_SCHED_INSTANCE_NAME, newSchedName);

                        // rename datasource
                        final String dsProp = PROP_JOB_STORE_PREFIX + ".dataSource";
                        final String dataSource = quartz.getProperty(dsProp);
                        if (empty(dataSource)) die("filterConfiguration: quartz config found but no "+dsProp+" found. Quartz properties: "+quartz.stringPropertyNames());
                        final String newDataSource = dataSource + "_" + rand;
                        quartz.setProperty(dsProp, newDataSource);

                        // scrub properties, replace datasource name in property names
                        boolean replacedUrl = false;
                        boolean replacedDs = false;
                        boolean replacedIdToken = false;
                        for (String name : quartz.stringPropertyNames()) {
                            String val = quartz.getProperty(name);
                            if (name.startsWith(PROP_DATASOURCE_PREFIX+"."+dataSource+".")) {
                                if (name.endsWith(DB_URL)) {
                                    val = database.getUrl();
                                    replacedUrl = true;
                                } else if (name.endsWith("identityToken")) {
                                    val = "id_token_"+newDataSource;
                                    replacedIdToken = true;
                                } else if (name.endsWith("dataSourceName")) {
                                    val = "dbPool_"+newDataSource;
                                    replacedDs = true;
                                }
                                quartz.remove(name);
                                quartz.setProperty(name.replace("."+dataSource+".", "."+newDataSource+"."), val);
                            }
                        }
                        if (!replacedUrl || !replacedDs || !replacedIdToken) {
                            die("filterConfiguration: quartz config found but some properties were missing. Quartz properties: "+quartz.stringPropertyNames());
                        }

                        // replace SCHED_NAME in database tables, if they become populated by restoring an older database
                        preRestoreTasks.add(new QuartzRestoreTask(newSchedName));
                    }
                }
            }

            if (hasQuartz) {
                database.addPostDataSourceSetupHandler(() -> {
                    try {
                        configuration.execSql("select count(*) from qrtz_triggers");
                    } catch (Exception e) {
                        log.debug("Quartz tables not found ("+e.getMessage()+"), creating them...");
                        configuration.execSqlCommands(QUARTZ_SQL_COMMANDS);
                    }
                });
            }
        }
        return configuration;
    }

    protected String getDatabaseNameSuffix() { return randomAlphanumeric(8).toLowerCase(); }

    @Override public void onStart(RestServer<C> server) {
        final RestServerConfiguration config = server.getConfiguration();
        config.setPublicUriBase("http://127.0.0.1:" +config.getHttp().getPort()+"/");

        final String[] postScripts = getSqlPostScripts();
        if (!empty(postScripts)) {
            for (String s : postScripts) {
                try {
                    getConfiguration().execSqlCommands(s);
                } catch (Exception e) {
                    die("onStart: SQL post-script ("+s+") failed: "+e, e);
                }
            }
        }
    }

    protected boolean createSqlIndexes () { return false; }
    protected String[] getSqlPostScripts() { return getConfiguration().getSqlConstraints(createSqlIndexes()); }

    // default resolution
    protected String resolveInclude(String path) { return loadResourceAsStringOrDie(path+".json"); }

    public void runScript(String script) throws Exception {
        new ApiRunner(getApi(), new ApiRunnerListenerBase(getClass().getName())).run(script);
    }

    @Override public void beforeStop(RestServer<C> server) {}
    @Override public void onStop(RestServer<C> server) {}

    protected static Map<String, RestServer> servers = new ConcurrentHashMap<>();
    private final AtomicReference<RestServer> server = new AtomicReference<>();
    public RestServer getServer () { return server.get(); }
    public C getConfiguration () { return (C) server.get().getConfiguration(); }

    protected <T> T getBean(Class<T> beanClass) { return getServer().getApplicationContext().getBean(beanClass); }

    public boolean useTestSpecificDatabase () { return false; }

    @Before public synchronized void startServer() throws Exception {
        ApiRunner.resetScripts();
        synchronized (server) {
            if (getServer() == null) {
                final String serverCacheKey = getServerCacheKey();
                if (servers.containsKey(serverCacheKey)) {
                    server.set(servers.get(serverCacheKey));
                } else {
                    final RestServerHarness<? extends RestServerConfiguration, ? extends RestServer> serverHarness
                            = new RestServerHarness<>(getRestServerClass());
                    serverHarness.setConfigurationSource(getConfigurationSource());
                    serverHarness.addConfigurationFilter(this);
                    serverHarness.init(getServerEnvironment());

                    final RestServer restServer = serverHarness.getServer();
                    restServer.getConfiguration().setTestMode(true);

                    this.server.set(restServer);
                    getServer().addLifecycleListener(this);
                    getServer().addLifecycleListener(new DbPoolShutdownListener());
                    getServer().addLifecycleListeners(getLifecycleListeners());
                    serverHarness.startServer();
                    servers.put(serverCacheKey, restServer);
                }
            }
        }
    }

    protected Collection<RestServerLifecycleListener> getLifecycleListeners() { return Collections.emptyList(); }

    protected void createDb(C config, String dbName) throws IOException { notSupported("createDb: must be defined in subclass"); }
    protected boolean dropDb(C config, String dbName, boolean background) throws IOException { return notSupported("dropDb: must be defined in subclass"); }

    @Override public void beforeStart(RestServer<C> server) {
        if (useTestSpecificDatabase()) {
            final String dbName = ((HasDatabaseConfiguration) server.getConfiguration()).getDatabase().getDatabaseName();
            if (dropPreExistingDatabase()) {
                try {
                    dropDb(server.getConfiguration(), dbName, false);
                } catch (Exception e) {
                    log.debug("beforeStart: error dropping database: " + dbName);
                }
            }
            try {
                createDb(server.getConfiguration(), dbName);
                tempDatabases.put(dbName, new DbDropper<>(this, server.getConfiguration(), dbName));
                log.info("beforeStart: "+getClass().getSimpleName()+" using database "+dbName);

            } catch (Exception e) {
                if (allowPreExistingDatabase()) {
                    log.warn("beforeStart: error creating database: " + dbName+": "+e);
                    server.getConfiguration().getDatabase().getHibernate().setHbm2ddlAuto("validate");
                } else {
                    die("beforeStart: error creating database: " + dbName + ": " + e);
                }
            }
        }
    }

    protected boolean dropPreExistingDatabase() { return true; }
    protected boolean allowPreExistingDatabase() { return false; }

    private String getTempDbNamePrefix(String url) {
        return truncate(url.substring(url.lastIndexOf('/') + 1), 15) + "_" + truncate(camelCaseToSnakeCase(getClass().getSimpleName()), 35).toLowerCase();
    }

    protected Map<String, String> getServerEnvironment() throws Exception { return null; }

    @Test public void ____stopServer () throws Exception {
        synchronized (server) {
            if (getServer() != null) {
                final PgRestServerConfiguration config = (C) getServer().getConfiguration();
                getServer().stopServer();
                if (useTestSpecificDatabase()) {
                    daemon(new DbDropper(this, config, ((HasDatabaseConfiguration) config).getDatabase().getDatabaseName()));
                }
            }
            server.set(null);
        }
    }

    private static final Map<String, DbDropper> tempDatabases = new ConcurrentHashMap<>();
    private static final Thread dbCleanup = new Thread(() -> {
        for (Map.Entry<String, DbDropper> entry : tempDatabases.entrySet()) {
            try {
                entry.getValue().drop();
                log.debug("shutdown-hook: successfully dropped db: "+entry.getKey());
            } catch (Exception e) {
                log.warn("shutdown-hook: error dropping db: "+entry.getKey());
            }
        }
    });
    static { Runtime.getRuntime().addShutdownHook(dbCleanup); }

    @AllArgsConstructor
    private static class DbDropper<C extends PgRestServerConfiguration> implements Runnable {

        public static final int DROP_DELAY = 30000;
        public static final int DROP_RETRY_INCR = 10000;

        @Getter private final AbstractResourceIT test;
        @Getter private final C configuration;
        @Getter private final String dbName;
;
        @Override public void run() {
            final String prefix = getClass().getSimpleName()+": ";
            int sleep = DROP_DELAY;
            for (int i=0; i<5; i++) {
                sleep(sleep);
                try {
                    if (drop()) {
                        log.debug(prefix+"successfully dropped test database: " + dbName);
                        return;
                    }
                    log.warn(prefix+"error dropping database: " + dbName);
                } catch (IOException e) {
                    log.warn(prefix+"error dropping database: " + dbName + ": " + e);
                }
                sleep += DROP_RETRY_INCR;
            }
            log.error("giving up trying to drop database: " + dbName);
        }

        protected boolean drop() throws IOException { return test.dropDb(configuration, dbName, true); }
    }

    protected Map<String, ConstraintViolationBean> mapViolations(ConstraintViolationBean[] violations) {
        final Map<String, ConstraintViolationBean> map = new HashMap<>(violations == null ? 1 : violations.length);
        for (ConstraintViolationBean violation : violations) {
            map.put(violation.getMessageTemplate(), violation);
        }
        return map;
    }

    protected void assertExpectedViolations(RestResponse response, String... violationMessages) throws Exception{
        assertEquals(HttpStatusCodes.UNPROCESSABLE_ENTITY, response.status);
        final ConstraintViolationBean[] violations = JsonUtil.FULL_MAPPER.readValue(response.json, ConstraintViolationBean[].class);
        assertExpectedViolations(violations, violationMessages);
    }

    protected void assertExpectedViolations(Collection<ConstraintViolationBean> violations, String... violationMessages) {
        assertExpectedViolations(violations.toArray(new ConstraintViolationBean[violations.size()]), violationMessages);
    }

    protected void assertExpectedViolations(ConstraintViolationBean[] violations, String... violationMessages) {
        final Map<String, ConstraintViolationBean> map = mapViolations(violations);
        assertEquals(violationMessages.length, map.size());
        for (String message : violationMessages) {
            assertTrue("assertExpectedViolations: key "+message+" not found in map: "+map, map.containsKey(message));
        }
    }

}
