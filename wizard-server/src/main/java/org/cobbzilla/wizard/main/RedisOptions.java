package org.cobbzilla.wizard.main;

import lombok.Getter;
import lombok.Setter;
import org.cobbzilla.util.collection.ArrayUtil;
import org.cobbzilla.util.main.BaseMainOptions;
import org.cobbzilla.util.string.StringUtil;
import org.cobbzilla.wizard.cache.redis.RedisConfiguration;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

public class RedisOptions extends BaseMainOptions {

    public static final String USAGE_HOST = "Redis hostname or IP";
    public static final String OPT_HOST = "-H";
    public static final String LONGOPT_HOST= "--host";
    @Option(name=OPT_HOST, aliases=LONGOPT_HOST, usage=USAGE_HOST)
    @Getter @Setter private String host = "127.0.0.1";

    public static final String USAGE_PORT = "Redis port";
    public static final String OPT_PORT = "-p";
    public static final String LONGOPT_PORT= "--port";
    @Option(name=OPT_PORT, aliases=LONGOPT_PORT, usage=USAGE_PORT)
    @Getter @Setter private int port = 6379;

    public static final String USAGE_KEY = "Redis encryption key (name of env var)";
    public static final String OPT_KEY = "-K";
    public static final String LONGOPT_KEY= "--key";
    @Option(name=OPT_KEY, aliases=LONGOPT_KEY, usage=USAGE_KEY)
    @Getter @Setter private String key = "REDIS_ENCRYPTION_KEY";

    public static final String USAGE_PREFIX = "Redis key prefix";
    public static final String OPT_PREFIX = "-P";
    public static final String LONGOPT_PREFIX= "--prefix";
    @Option(name=OPT_PREFIX, aliases=LONGOPT_PREFIX, usage=USAGE_PREFIX)
    @Getter @Setter private String prefix = "";

    public static final String USAGE_QUIET = "Quiet output";
    public static final String OPT_QUIET = "-q";
    public static final String LONGOPT_QUIET= "--quiet";
    @Option(name=OPT_QUIET, aliases=LONGOPT_QUIET, usage=USAGE_QUIET)
    @Getter @Setter private boolean quiet = false;

    public static final String USAGE_DISABLE_KEYS_WC = "By default, the argument to the 'keys' command is enclosed in '*' characters. Enable this flag to disable that behavior.";
    public static final String OPT_DISABLE_KEYS_WC = "-w";
    public static final String LONGOPT_DISABLE_KEYS_WC= "--disable-keys-wildcard";
    @Option(name=OPT_DISABLE_KEYS_WC, aliases=LONGOPT_DISABLE_KEYS_WC, usage=USAGE_DISABLE_KEYS_WC)
    @Getter @Setter private boolean disableKeysWildcard = false;

    public static final String USAGE_ARGS = "Redis command and arguments";
    @Argument(multiValued=true, required=true)
    @Getter @Setter private String[] args;

    public RedisConfiguration getRedisConfiguration() {
        return new RedisConfiguration(getHost(), getPort(), System.getenv(getKey()), getPrefix());
    }

    public String getCommand() { return empty(args) ? null : args[0]; }

    public String[] getArguments() {
        return empty(args) ? StringUtil.EMPTY_ARRAY : ArrayUtil.slice(args, 1, args.length);
    }

    public String[] getArgumentsAfterFirst() {
        return empty(args) || args.length <= 2 ? StringUtil.EMPTY_ARRAY : ArrayUtil.slice(args, 2, args.length);
    }

    public String firstArgument() { return nthArgument(0); }
    public String secondArgument() { return nthArgument(1); }
    public String nthArgument(int n) {
        final String[] arguments = getArguments();
        return empty(arguments) || arguments.length <= n ? null : arguments[n];
    }
}
