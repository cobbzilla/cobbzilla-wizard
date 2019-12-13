package org.cobbzilla.wizard.main;

import lombok.Getter;
import lombok.Setter;
import org.cobbzilla.util.main.BaseMainOptions;
import org.cobbzilla.util.system.CommandShell;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.io.FileUtil.abs;
import static org.cobbzilla.util.reflect.ReflectionUtil.forName;
import static org.cobbzilla.util.string.StringUtil.chop;
import static org.cobbzilla.util.string.StringUtil.uncapitalize;

public class ConfigurationDataBagOptions extends BaseMainOptions {

    public static final String USAGE_SERVER_CLASS = "Server class";
    public static final String OPT_SERVER_CLASS = "-s";
    public static final String LONGOPT_SERVER_CLASS= "--server-class";
    @Option(name=OPT_SERVER_CLASS, aliases=LONGOPT_SERVER_CLASS, usage=USAGE_SERVER_CLASS)
    @Setter private String serverClass = null;

    public String getServerClass() {
        if (serverClass == null) {
            serverClass = CommandShell.execScript("find src/main/java -type f -name \"*.java\" | xargs grep -l 'extends RestServerBase'").trim();
            if (empty(serverClass)) die("No classes found that implement RestServerBase, but detection is not that great. Use "+OPT_SERVER_CLASS+"/"+LONGOPT_SERVER_CLASS+" to specify server class");
            if (serverClass.indexOf('\n') != -1) die("Multiple classes found in src dir that extended RestServerBase: "+serverClass+"\nUse "+OPT_SERVER_CLASS+"/"+LONGOPT_SERVER_CLASS+" to specify server class");
            serverClass = chop(serverClass, ".java");
            serverClass = serverClass.substring("src/main/java/".length()).replace("/", ".");
        }
        return serverClass;
    }

    private String getServerName() { return getServerName(getServerClass()); }

    private String getServerName(String serverClass) {
        return uncapitalize(chop(forName(serverClass).getSimpleName(), "Server"));
    }

    public static final String USAGE_ENV_FILE = "Environment file";
    public static final String OPT_ENV_FILE = "-e";
    public static final String LONGOPT_ENV_FILE= "--env-file";
    @Option(name=OPT_ENV_FILE, aliases=LONGOPT_ENV_FILE, usage=USAGE_ENV_FILE)
    @Getter @Setter private File envFile = null;

    public static final String USAGE_CONFIG_FILE = "Configuration YAML file";
    public static final String OPT_CONFIG_FILE = "-c";
    public static final String LONGOPT_CONFIG_FILE= "--conf-file";
    @Option(name=OPT_CONFIG_FILE, aliases=LONGOPT_CONFIG_FILE, usage=USAGE_CONFIG_FILE)
    @Setter private File configFile = null;

    public File getConfigFile() {
        if (configFile == null) {
            final File default1 = new File("src/main/resources/" + getServerName() + ".yml");
            final File default2 = new File("src/main/resources/" + getServerName() + "-config.yml");
            configFile = default1.exists() ? default1 : default2.exists() ? default2 : null;
            if (configFile == null) die("Default YML config not found ("+abs(default1)+" or "+abs(default2)+") use "+OPT_CONFIG_FILE+"/"+LONGOPT_CONFIG_FILE);
            err("info: using default config YML file: "+abs(configFile));
        }
        return configFile;
    }

    public static final String USAGE_OUT_FILE = "Output file. Default is stdout";
    public static final String OPT_OUT_FILE = "-o";
    public static final String LONGOPT_OUT_FILE= "--output-file";
    @Option(name=OPT_OUT_FILE, aliases=LONGOPT_OUT_FILE, usage=USAGE_OUT_FILE)
    @Getter @Setter private File output = null;
    public boolean hasOutput() { return output != null; }

    public Map<String, String> getEnv() throws IOException {
        if (getEnvFile() == null) {
            File default1 = new File(System.getProperty("user.home") + "/." + getServerName() + ".env");
            File default2 = new File(System.getProperty("user.home") + "/." + getServerName() + "-dev.env");
            setEnvFile(default1.exists() ? default1 : default2.exists() ? default2 : null);
            if (getEnvFile() == null) die("Default env file not found ("+abs(default1)+" or "+abs(default2)+") use "+OPT_ENV_FILE+"/"+LONGOPT_ENV_FILE);
            err("info: using default env file: "+abs(getEnvFile()));
        }
        return CommandShell.loadShellExports(getEnvFile());
    }

    public static final String USAGE__TIMEOUT = "Timeout";
    public static final String OPT__TIMEOUT = "-t";
    public static final String LONGOPT__TIMEOUT= "--timeout";
    @Option(name=OPT__TIMEOUT, aliases=LONGOPT__TIMEOUT, usage=USAGE__TIMEOUT)
    @Getter @Setter private long timeout = TimeUnit.MINUTES.toMillis(1);

}
