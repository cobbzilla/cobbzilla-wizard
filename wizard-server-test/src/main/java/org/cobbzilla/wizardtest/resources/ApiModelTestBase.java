package org.cobbzilla.wizardtest.resources;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.SingletonList;
import org.cobbzilla.util.io.FileUtil;
import org.cobbzilla.util.jdbc.UncheckedSqlException;
import org.cobbzilla.util.system.Sleep;
import org.cobbzilla.wizard.client.ApiClientBase;
import org.cobbzilla.wizard.client.script.ApiRunner;
import org.cobbzilla.wizard.client.script.ApiRunnerListenerBase;
import org.cobbzilla.wizard.client.script.ApiRunnerMultiListener;
import org.cobbzilla.wizard.client.script.ApiScriptIncludeHandler;
import org.cobbzilla.wizard.model.entityconfig.ModelSetupListener;
import org.cobbzilla.wizard.model.entityconfig.ModelSetupListenerBase;
import org.cobbzilla.wizard.server.RestServer;
import org.cobbzilla.wizard.server.config.DatabaseConfiguration;
import org.cobbzilla.wizard.server.config.HasDatabaseConfiguration;
import org.cobbzilla.wizard.server.config.PgRestServerConfiguration;
import org.cobbzilla.wizard.server.config.RestServerConfiguration;
import org.junit.Before;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.System.identityHashCode;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.io.FileUtil.*;
import static org.cobbzilla.util.reflect.ReflectionUtil.instantiate;
import static org.cobbzilla.util.system.CommandShell.execScript;
import static org.cobbzilla.wizard.model.entityconfig.ModelSetup.modelHash;
import static org.cobbzilla.wizard.model.entityconfig.ModelSetup.setupModel;

@Slf4j
public abstract class ApiModelTestBase<C extends PgRestServerConfiguration, S extends RestServer<C>>
        extends ApiDocsResourceIT<C, S>
        implements ApiScriptIncludeHandler {

    protected String getModelPrefix() { return "models/"; }
    protected String getEntityConfigsEndpoint() { return "/ec"; }

    protected String getBaseUri() { return getConfiguration().getApiUriBase(); }

    @Getter private final AtomicReference<ApiRunner> _defaultRunner = new AtomicReference<>();

    protected ApiRunner getApiRunner() {
        return new ApiRunner(new ApiClientBase(getBaseUri()), new ApiRunnerListenerBase(getBaseUri()));
    }

    @Override public boolean useTestSpecificDatabase () { return true; }

    protected String getManifest() { return "manifest"; }
    protected String getModelRunName() { return getClass().getSimpleName(); }
    protected long getModelSetupTimeout () { return TimeUnit.MINUTES.toMillis(5); }

    protected Class<? extends ModelSetupListener> getModelSetupListenerClass() { return ModelSetupListenerBase.class; }
    @Getter(lazy=true) private final ModelSetupListener modelSetupListener = instantiate(getModelSetupListenerClass(), getConfiguration());

    public ApiRunnerMultiListener getApiListener() {
        final ApiRunnerMultiListener listener = new ApiRunnerMultiListener(getClass().getName());
        if (docsEnabled) listener.addApiListener(apiDocsRunnerListener);
        return listener;
    }
    @Before public void reset() throws Exception { setSystemTimeOffset(0); }

    @Override public void onStart(RestServer<C> server) {
        super.onStart(server);
        try {
            setup(getModelSetupListener(), getModelPrefix(), getManifest(), doTruncateDb(), getModelRunName());
        } catch (Exception e) {
            die("onStart: error calling setup: "+e, e);
        }
    }

    public boolean doTruncateDb() { return true; }

    public static final String SETUP_LOCK = ".lock";
    private final static Map<String, AtomicReference<String>> currentModels = new ConcurrentHashMap<>();

    protected synchronized void setup(ModelSetupListener listener, String modelPrefix, String manifest, boolean truncate, String runName) throws Exception {
        final String logPrefix = "setup: " + modelPrefix + manifest + " - ";
        final File cacheFile = permCacheFile(modelPrefix, manifest);
        if (cacheFile == null) {
            log.warn(logPrefix+"no cacheFile could be determined, or caching disabled");
            setup_internal(listener, modelPrefix, manifest, truncate, null, runName);
            return;
        }

        final String cacheKey = abs(cacheFile);
        final String modelCacheKey = getClass().getName();
        final AtomicReference<String> currentModel;
        if (!currentModels.containsKey(modelCacheKey)) {
            synchronized (currentModels) {
                if (!currentModels.containsKey(modelCacheKey)) {
                    currentModels.put(modelCacheKey, new AtomicReference<String>());
                }
            }
        }
        currentModel = currentModels.get(modelCacheKey);

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (currentModel) {
            if (currentModel.get() != null && currentModel.get().equals(cacheKey)) return;
            if (hasPermCache(modelPrefix, manifest)) {
                log.info(logPrefix + "restoring from " + abs(cacheFile) + ", size=" + cacheFile.length());
                pgTruncateAndRestore(cacheFile);
                currentModel.set(cacheKey);
                return;
            }

            final File lockFile = new File(abs(cacheFile) + SETUP_LOCK);

            final String setupKey = getClass().getSimpleName() + "/" + identityHashCode(this) + "/" + randomAlphanumeric(10);
            final String lockPath = abs(lockFile);
            log.info(logPrefix + "lockFile: " + lockPath + " (contents=" + FileUtil.toString(lockFile) + ")");
            if (!lockFile.exists() || realNow() - lockFile.lastModified() > getModelSetupTimeout()) {
                FileUtil.toFile(lockFile, setupKey);
                final String otherTest = FileUtil.toString(lockFile);
                if (otherTest.equals(setupKey)) {
                    log.info(logPrefix + "claiming lockFile and doing setup: " + lockPath + " (contents=" + FileUtil.toString(lockFile) + ")");
                    setup_internal(listener, modelPrefix, manifest, truncate, cacheFile, runName);
                    if (!lockFile.delete()) log.warn(logPrefix + "error deleting lock file: " + lockPath);

                } else {
                    waitForModel(listener, modelPrefix, manifest, truncate, cacheFile, lockFile, runName);
                }
            } else {
                waitForModel(listener, modelPrefix, manifest, truncate, cacheFile, lockFile, runName);
            }
            currentModel.set(cacheKey);
        }
    }

    private void waitForModel(ModelSetupListener listener, String modelPrefix, String manifest, boolean truncate, File cacheFile, File lockFile, String runName) throws Exception {
        // someone else got here first, wait for them
        final String logPrefix = "setup: " + modelPrefix + manifest + " - ";
        final String otherTest = FileUtil.toString(lockFile);
        final String lockPath = abs(lockFile);
        log.info(logPrefix+"waiting on lockFile: "+ lockPath +" (contents="+otherTest+")");
        long start = realNow();
        while (lockFile.exists() && realNow() - start < getModelSetupTimeout()) {
            Sleep.sleep(1000, "waiting for another test ("+otherTest+") to finish building model");
        }
        if (!cacheFile.exists() && realNow() - start > getModelSetupTimeout()) {
            die(logPrefix+"timed out waiting for another test ("+otherTest+") to finish building model");
        }
        if (cacheFile.exists()) {
            log.info(logPrefix+"lockFile absent, other test ("+otherTest+") finished setting up model, restoring from "+ abs(cacheFile));
            pgTruncateAndRestore(cacheFile);
        } else {
            log.info(logPrefix+"lockFile absent but cacheFile missing ("+ abs(cacheFile)+"), re-trying setup");
            setup(listener, modelPrefix, manifest, truncate, runName);
        }
    }

    private void setup_internal(ModelSetupListener listener, String modelPrefix, String manifest, boolean truncate, File cacheFile, String runName) throws Exception {
        final C config = getConfiguration();
        if (truncate) truncateTables(config);
        seed(config);
        buildModel(listener, modelPrefix, manifest, runName);
        if (permCacheDir() != null && cacheFile != null) pgDump(cacheFile);
    }

    protected void seed(C config) {}

    public LinkedHashMap<String, String> buildModel(ModelSetupListener listener, String modelPrefix, String manifest, String runName) throws Exception {
        return setupModel(getApi(), getEntityConfigsEndpoint(), modelPrefix, manifest, listener, runName);
    }

    public File permCacheDir() {
        final String modelCache = getConfiguration().getEnvironment().get("MODEL_CACHE");
        return !empty(modelCache) ? new File(modelCache) : new File("/tmp/model-cache");
    }

    private boolean permCacheExists(String prefix, String manifest) {
        return permCacheDir() != null && permCacheFile(prefix, manifest).exists();
    }

    protected File permCacheFile(String prefix, String manifest) {
        final File permCacheDir = permCacheDir();
        if (permCacheDir == null) return null;
        FileUtil.mkdirOrDie(permCacheDir);
        return new File(permCacheDir, permCacheFileName(prefix, manifest));
    }

    private String permCacheKey(String prefix, String manifest) { return modelHash(prefix, manifest); }
    private String permCacheFileName(String prefix, String manifest) { return "model-db-snapshot-"+permCacheKey(prefix, manifest)+".sql"; }

    protected boolean hasPermCache(String prefix, String manifest) { return permCacheDir() != null && permCacheExists(prefix, manifest); }

    protected DatabaseConfiguration getDbConfig(RestServerConfiguration config) { return ((HasDatabaseConfiguration) config).getDatabase(); }

    @Override protected void createDb(C config, String dbName) throws IOException {
        execScript(config.pgCommandString("createdb", null, "postgres"), config.pgEnv());
    }

    public static final List<Integer> DROP_EXIT_VALUES = Arrays.asList(0, 1);

    @Override protected boolean dropDb (C config, String dbName, boolean background) {
        final String dropCommand = config.pgCommandString("dropdb", null, "postgres");
        final String command = background ? "set -m ; " + dropCommand + " &" : dropCommand;
        execScript(command, config.pgEnv(), DROP_EXIT_VALUES);
        return true;
    }

    // from: https://stackoverflow.com/a/2829485/1251543
    private static final String TRUNCATE_ALL_TABLES
            = "\nCREATE OR REPLACE FUNCTION truncate_tables(username IN VARCHAR) RETURNS void AS $$\n" +
            "DECLARE\n" +
            "    statements CURSOR FOR\n" +
            "        SELECT tablename FROM pg_tables\n" +
            "        WHERE tableowner = username AND schemaname = 'public';\n" +
            "BEGIN\n" +
            "    FOR stmt IN statements LOOP\n" +
            "        EXECUTE 'TRUNCATE TABLE ' || quote_ident(stmt.tablename) || ' CASCADE;';\n" +
            "    END LOOP;\n" +
            "END;\n" +
            "$$ LANGUAGE plpgsql;\n";

    public static String getTruncateTablesSql (String user) { return TRUNCATE_ALL_TABLES+"\nSELECT truncate_tables('" + user + "');"; }

    protected void truncateTables(C config) {
        try {
            config.execSql(getTruncateTablesSql(getDbConfig(config).getUser()));
        } catch (UncheckedSqlException e) {
            log.warn("truncateTables: SQLException: "+e.getMessage());
        }
    }

    protected void pgRestore(File file) { pgRestore(file, false); }
    protected void pgTruncateAndRestore(File file) { pgRestore(file, true); }

    protected void pgRestore(File file, boolean truncate) {
        final C config = getConfiguration();
        final File temp;
        if (truncate) {
            temp = temp("pgRestore", ".sql");
            writeStringOrDie(temp, getTruncateTablesSql(getDbConfig(config).getUser()));
        } else {
            temp = null;
        }

        for (PreRestoreTask task : preRestoreTasks) {
            file = task.handle(file);
        }

        final String path = abs(file);
        final String output = execScript("cat " + (truncate ? abs(temp) + " " : "") + path + " | " + config.pgCommand(), config.pgEnv());
        if (output.contains("ERROR")) die("pgRestore: error restoring from snapshot "+ path +":\n"+output);

        for (Runnable task : postRestoreTasks) task.run();

        log.info(getClass().getSimpleName()+": restored DB from snapshot: "+ path);
    }

    protected void pgDump(File file) {
        final String simpleName = getClass().getSimpleName();
        if (file.exists()) {
            log.warn("pgDump("+simpleName+"): Not dumping to "+ abs(file)+", file already exists");
            return;
        }
        getConfiguration().pgDump(file);
    }

    protected void modelTest(final String name) throws Exception {
        modelTest(name, getApiRunner());
    }

    protected void modelTest(final String name, ApiRunner apiRunner) throws Exception {
        logout();
        apiRunner.run(include(name));
    }

    @Override public List<String> getIncludePaths() {
        return new SingletonList<>(getModelPrefix() + (getModelPrefix().endsWith("/") ? "" : File.separator) + "tests");
    }

}
