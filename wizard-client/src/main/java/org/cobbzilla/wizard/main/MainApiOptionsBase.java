package org.cobbzilla.wizard.main;

import lombok.Getter;
import lombok.Setter;
import org.cobbzilla.util.main.BaseMainOptions;
import org.kohsuke.args4j.Option;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

public abstract class MainApiOptionsBase extends BaseMainOptions {

    public static final String USAGE_ACCOUNT = "The account name, or @ENV_VAR to specify which environment variable to check. Default is to check the API_ACCOUNT environment variable";
    public static final String OPT_ACCOUNT = "-a";
    public static final String LONGOPT_ACCOUNT = "--account";
    @Option(name=OPT_ACCOUNT, aliases=LONGOPT_ACCOUNT, usage=USAGE_ACCOUNT, required=false)
    @Setter private String account = getDefaultAccount();

    protected String getDefaultAccount() { return "@API_ACCOUNT"; }

    public String getAccount() {
        final String name = account.startsWith("@") ? System.getenv(account.substring(1)) : account;
        if (requireAccount() && empty(name)) die("No account provided (use option "+OPT_ACCOUNT+"/"+LONGOPT_ACCOUNT+")");
        return name;
    }
    public boolean hasAccount () { return !empty(getAccount()) && !getAccount().startsWith("@"); }

    public static final String USAGE_TWO_FACTOR = "The token for two-factor authentication";
    public static final String OPT_TWO_FACTOR = "-F";
    public static final String LONGOPT_TWO_FACTOR = "--two-factor";
    @Option(name=OPT_TWO_FACTOR, aliases=LONGOPT_TWO_FACTOR, usage=USAGE_TWO_FACTOR)
    @Getter @Setter private String twoFactor;

    public boolean hasTwoFactor () { return !empty(twoFactor); }

    public static final String USAGE_API_BASE = "The server's API base URI.";
    public static final String OPT_API_BASE = "-s";
    public static final String LONGOPT_API_BASE = "--server";
    @Option(name=OPT_API_BASE, aliases=LONGOPT_API_BASE, usage=USAGE_API_BASE)
    @Setter private String apiBase = getDefaultApiBaseUri();

    public String getApiBase() {
        if (empty(apiBase)) die("No API base URI defined!");
        return apiBase.startsWith("@") ? System.getenv(apiBase.substring(1)) : apiBase;
    }

    protected String getDefaultApiBaseUri() { return "@API_SERVER"; }

    @Getter private final String password = initPassword();
    public boolean hasPassword () { return !empty(getPassword()) && password != INVALID_PASSWORD; }

    public boolean requireAccount() { return true; }

    private static final String INVALID_PASSWORD = " -- password not set -- ";

    private String initPassword() {
        final String pass = System.getenv(getPasswordEnvVarName());
        if (empty(pass) && requireAccount()) {
            System.err.println("Warning: " + getPasswordEnvVarName() + " not defined in environment");
            return INVALID_PASSWORD;
        }
        return pass;
    }

    protected abstract String getPasswordEnvVarName();

}
