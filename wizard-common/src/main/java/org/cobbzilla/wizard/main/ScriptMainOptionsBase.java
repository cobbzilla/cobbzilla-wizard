package org.cobbzilla.wizard.main;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;
import org.cobbzilla.util.io.TempDir;
import org.cobbzilla.util.json.JsonUtil;
import org.cobbzilla.wizard.client.script.ApiRunnerListener;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.daemon.ZillaRuntime.readStdin;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.reflect.ReflectionUtil.instantiate;

public abstract class ScriptMainOptionsBase extends MainApiOptionsBase {

    public static final String USAGE_NOLOGIN = "Do not login before running scripts. Default is true.";
    public static final String OPT_NOLOGIN = "-L";
    public static final String LONGOPT_NOLOGIN= "--no-login";
    @Option(name=OPT_NOLOGIN, aliases=LONGOPT_NOLOGIN, usage=USAGE_NOLOGIN)
    @Getter @Setter private boolean noLogin = false;

    @Override public boolean requireAccount() { return !noLogin; }

    public static final String USAGE_INCLUDE_MAILBOX = "include response checks that involve the \"mailbox\" variable. Default is false";
    public static final String OPT_INCLUDE_MAILBOX = "-M";
    public static final String LONGOPT_INCLUDE_MAILBOX= "--include-mailbox";
    @Option(name=OPT_INCLUDE_MAILBOX, aliases=LONGOPT_INCLUDE_MAILBOX, usage=USAGE_INCLUDE_MAILBOX)
    @Getter @Setter private boolean includeMailbox = false;

    public static final String USAGE_CAPTURE_HEADERS = "capture HTTP response headers. Default is false";
    public static final String OPT_CAPTURE_HEADERS = "-H";
    public static final String LONGOPT_CAPTURE_HEADERS= "--headers";
    @Option(name=OPT_CAPTURE_HEADERS, aliases=LONGOPT_CAPTURE_HEADERS, usage=USAGE_CAPTURE_HEADERS)
    @Getter @Setter private boolean captureHeaders = false;

    public static final String USAGE_SKIP_CHECKS = "skip all response checks. Default is false";
    public static final String OPT_SKIP_CHECKS = "-K";
    public static final String LONGOPT_SKIP_CHECKS= "--skip-all";
    @Option(name=OPT_SKIP_CHECKS, aliases=LONGOPT_SKIP_CHECKS, usage=USAGE_SKIP_CHECKS)
    @Getter @Setter private boolean skipAllChecks = false;

    public static final String USAGE_INCLUDE_BASEDIR = "base directory for include files. Default is the directory of the first script argument";
    public static final String OPT_INCLUDE_BASEDIR = "-I";
    public static final String LONGOPT_INCLUDE_BASEDIR= "--include-base";
    @Option(name=OPT_INCLUDE_BASEDIR, aliases=LONGOPT_INCLUDE_BASEDIR, usage=USAGE_INCLUDE_BASEDIR)
    @Getter @Setter private File includeBaseDir = null;
    public boolean hasIncludeBaseDir () { return includeBaseDir != null; }

    public static final String USAGE_BEFORE = "before handler class";
    public static final String OPT_BEFORE = "-B";
    public static final String LONGOPT_BEFORE= "--before";
    @Option(name=OPT_BEFORE, aliases=LONGOPT_BEFORE, usage=USAGE_BEFORE)
    @Getter @Setter private String beforeHandler;

    @Getter private File tempDir = new TempDir();

    public boolean hasBeforeHandler () { return !empty(beforeHandler); }

    @Getter(lazy=true) private final ApiRunnerListener beforeHandlerObject = initBefore();
    private ApiRunnerListener initBefore() {
        return hasBeforeHandler() ? (ApiRunnerListener) instantiate(beforeHandler) : null;
    }

    public static final String USAGE_AFTER = "after handler class";
    public static final String OPT_AFTER = "-A";
    public static final String LONGOPT_AFTER= "--after";
    @Option(name=OPT_AFTER, aliases=LONGOPT_AFTER, usage=USAGE_AFTER)
    @Getter @Setter private String afterHandler;
    public boolean hasAfterHandler () { return !empty(afterHandler); }

    @Getter(lazy=true) private final ApiRunnerListener afterHandlerObject = initAfter();
    private ApiRunnerListener initAfter() {
        return hasAfterHandler() ? (ApiRunnerListener) instantiate(afterHandler) : null;
    }

    public static final String USAGE_VAR = "define script variables. Argument is a single-quoted string of a JSON object, which maps variable names to JSON values. If this is - then JSON will be read from stdin.";
    public static final String OPT_VAR = "-V";
    public static final String LONGOPT_VAR= "--vars";
    @Option(name=OPT_VAR, aliases=LONGOPT_VAR, usage=USAGE_VAR)
    @Getter @Setter public String variableDefinitions;
    @Getter(lazy=true) private final Map<String, Object> scriptVars = initScriptVars();
    private Map<String, Object> initScriptVars() {
        final Map<String, Object> vars = new HashMap<>();
        if (hasScriptVars()) {
            if (variableDefinitions.equals("-")) {
                variableDefinitions = readStdin();
            }
            final JsonNode node = json(variableDefinitions, JsonNode.class);
            for (Iterator<String> iter = node.fieldNames(); iter.hasNext(); ) {
                final String name = iter.next();
                final JsonNode val = node.get(name);
                vars.put(name, JsonUtil.getNodeAsJava(val, name));
            }
        }
        return vars;
    }
    public boolean hasScriptVars () { return !empty(variableDefinitions); }

    public static final String USAGE_CALL_INCLUDE = "Call an include file directly. Can only supply one script file. Does not work with stdin scripts.";
    public static final String OPT_CALL_INCLUDE = "-C";
    public static final String LONGOPT_CALL_INCLUDE= "--call-include";
    @Option(name=OPT_CALL_INCLUDE, aliases=LONGOPT_CALL_INCLUDE, usage=USAGE_CALL_INCLUDE)
    @Getter @Setter private boolean callInclude = false;

    @Argument(usage="script files to run, in order. Default is stdin")
    @Getter @Setter private File[] scripts;
    public boolean hasScripts () { return !empty(scripts); }

    public char getParamStartDelim() { return '<'; }
    public char getParamEndDelim() { return '>'; }

}
