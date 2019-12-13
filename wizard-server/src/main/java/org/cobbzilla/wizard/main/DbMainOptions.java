package org.cobbzilla.wizard.main;

import lombok.Getter;
import lombok.Setter;
import org.cobbzilla.util.main.BaseMainOptions;
import org.cobbzilla.wizard.server.RestServer;
import org.cobbzilla.wizard.server.RestServerHarness;
import org.cobbzilla.wizard.server.config.DatabaseConfiguration;
import org.cobbzilla.wizard.server.config.HasDatabaseConfiguration;
import org.cobbzilla.wizard.server.config.RestServerConfiguration;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.reflect.ReflectionUtil.forName;
import static org.cobbzilla.util.system.CommandShell.loadShellExportsOrDie;
import static org.cobbzilla.wizard.server.RestServerBase.getStreamConfigurationSource;

public abstract class DbMainOptions extends BaseMainOptions {

    public static final File DEFAULT_ENV_FILE = new File(System.getProperty("user.home"), ".db.env");

    public abstract String getServerClass();
    public abstract String getConfigPath();
    public abstract String getDefaultCryptEnvVar();

    public static final String USAGE_ENV_FILE = "Environment file. Default is ~/db.env";
    public static final String OPT_ENV_FILE = "-e";
    public static final String LONGOPT_ENV_FILE= "--env-file";
    @Option(name=OPT_ENV_FILE, aliases=LONGOPT_ENV_FILE, usage=USAGE_ENV_FILE)
    @Getter @Setter private File envFile = DEFAULT_ENV_FILE;

    public static final String USAGE_CRYPT_ENV_VAR = "Name of env var containing encryption/decryption key within env file";
    public static final String OPT_CRYPT_ENV_VAR = "-C";
    public static final String LONGOPT_CRYPT_ENV_VAR= "--crypt-var";
    @Option(name=OPT_CRYPT_ENV_VAR, aliases=LONGOPT_CRYPT_ENV_VAR, usage=USAGE_CRYPT_ENV_VAR)
    @Getter @Setter private String cryptKeyEnvVar = getDefaultCryptEnvVar();

    public static final String USAGE_DECRYPT_ENV_VAR = "Name of env var containing decryption key to use for column-level encryption when reading.";
    public static final String OPT_DECRYPT_ENV_VAR = "-D";
    public static final String LONGOPT_DECRYPT_ENV_VAR= "--decrypt-with";
    @Option(name=OPT_DECRYPT_ENV_VAR, aliases=LONGOPT_DECRYPT_ENV_VAR, usage=USAGE_DECRYPT_ENV_VAR)
    @Getter @Setter private String decryptKeyEnvVar = getDefaultCryptEnvVar();

    public static final String USAGE_ENCRYPT_ENV_VAR = "Name of env var containing encryption key to use for column-level encryption when writing.";
    public static final String OPT_ENCRYPT_ENV_VAR = "-E";
    public static final String LONGOPT_ENCRYPT_ENV_VAR= "--encrypt-with";
    @Option(name=OPT_ENCRYPT_ENV_VAR, aliases=LONGOPT_ENCRYPT_ENV_VAR, usage=USAGE_ENCRYPT_ENV_VAR)
    @Getter @Setter private String encryptKeyEnvVar = null;

    public static final String USAGE_IGNORE_UNKNOWN = "If false, refuse to run if tables or columns in scrub file do not exist in database. Default is true, in which case they are skipped.";
    public static final String OPT_IGNORE_UNKNOWN = "-I";
    public static final String LONGOPT_IGNORE_UNKNOWN= "--ignore-unknown";
    @Option(name=OPT_IGNORE_UNKNOWN, aliases=LONGOPT_IGNORE_UNKNOWN, usage=USAGE_IGNORE_UNKNOWN)
    @Getter @Setter private boolean ignoreUnknown = true;

    public <C extends RestServerConfiguration, S extends RestServer<C>> HasDatabaseConfiguration getDatabaseConfiguration() {
        return getDatabaseConfiguration(getConfigPath(), getDecryptKeyEnvVar());
    }

    public <C extends RestServerConfiguration, S extends RestServer<C>> HasDatabaseConfiguration getDatabaseConfiguration(String path, String envVar) {
        final String key;
        if (envVar != null) {
            key = System.getenv(envVar);
            if (empty(key)) die("env var not found: " + envVar);
        } else {
            key = null;
        }

        final Class<S> serverClass = (Class<S>) forName(getServerClass());
        final RestServerHarness<C, S> harness = new RestServerHarness<>(serverClass);

        harness.setConfigurationSource(getStreamConfigurationSource(serverClass, path));
        Map<String, String> env;
        try {
            env = loadShellExportsOrDie(getEnvFile());
        } catch (Exception e) {
            err("error loading exports from "+getEnvFile()+": "+e);
            env = new HashMap<>();
        }
        if (key != null) env.put(getDefaultCryptEnvVar(), key);
        harness.init(env);

        return (HasDatabaseConfiguration) harness.getConfiguration();
    }

    public DatabaseConfiguration getDatabase() { return getDatabaseConfiguration().getDatabase(); }

    public <C extends RestServerConfiguration, S extends RestServer<C>> HasDatabaseConfiguration getDatabaseReadConfiguration() {
        return getDatabaseConfiguration(getConfigPath(), getDecryptKeyEnvVar());
    }

    public <C extends RestServerConfiguration, S extends RestServer<C>> HasDatabaseConfiguration getDatabaseWriteConfiguration() {
        return getDatabaseConfiguration(getConfigPath(), getEncryptKeyEnvVar());
    }

}
