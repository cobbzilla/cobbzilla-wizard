package org.cobbzilla.wizard.main;

import fr.opensagres.xdocreport.core.io.IOUtils;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.daemon.ZillaRuntime;
import org.cobbzilla.util.handlebars.HandlebarsUtil;
import org.cobbzilla.util.http.HttpStatusCodes;
import org.cobbzilla.util.io.FileUtil;
import org.cobbzilla.util.javascript.JsEngine;
import org.cobbzilla.util.javascript.StandardJsEngine;
import org.cobbzilla.util.main.BaseMain;
import org.cobbzilla.wizard.auth.LoginRequest;
import org.cobbzilla.wizard.client.ApiClientBase;
import org.cobbzilla.wizard.client.script.*;
import org.cobbzilla.wizard.util.RestResponse;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.apache.commons.io.IOUtils.copy;
import static org.apache.commons.io.IOUtils.copyLarge;
import static org.cobbzilla.util.collection.ArrayUtil.arrayToString;
import static org.cobbzilla.util.collection.ArrayUtil.slice;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.io.FileUtil.abs;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;
import static org.cobbzilla.util.string.StringUtil.urlEncode;
import static org.cobbzilla.util.system.Sleep.sleep;
import static org.cobbzilla.util.time.TimeUtil.DAY;
import static org.cobbzilla.util.time.TimeUtil.parseDuration;
import static org.cobbzilla.wizard.main.ScriptMainOptionsBase.*;

@Slf4j
public abstract class ScriptMainBase<OPT extends ScriptMainOptionsBase>
        extends MainApiBase<OPT>
        implements ApiScriptIncludeHandler {

    @Override protected void run() throws Exception {
        final OPT options = getOptions();
        final ApiRunner runner = getApiRunner();
        final File[] scripts = options.getScripts();
        if (options.isCallInclude()) {
            if (scripts.length != 1) {
                die("When "+OPT_CALL_INCLUDE+"/"+LONGOPT_CALL_INCLUDE+" is used, exactly one script argument must be provided");
            }
            final ApiScript wrapper = new ApiScript()
                    .setInclude(abs(scripts[0]))
                    .setParams(options.getScriptVars());
            runScript(getApiClient(), options, json(new ApiScript[]{wrapper}), runner);

        } else if (options.hasScripts()) {
            for (File f : scripts) {
                if (f.exists()) {
                    final String script = FileUtil.toString(f);
                    if (options.hasScriptVars()) {
                        runScript(getApiClient(), options, script, runner);
                    } else {
                        runScript(runner, script);
                    }
                } else {
                    out("SKIPPING (does not exist): "+abs(f));
                }
            }
        } else {
            runScript(runner, IOUtils.toString(System.in));
        }
    }

    @Override protected Object buildLoginRequest(OPT options) {
        return new LoginRequest(options.getAccount(), options.getPassword());
    }

    @Override protected void setSecondFactor(Object loginRequest, String token) { notSupported("setSecondFactor"); }

    public static final String AFTER_SAVE_DOWNLOAD = "save-download-to-file";
    public static final String SLEEP = "sleep";

    public boolean clockAdjusted = false;

    protected void runScript(ApiRunner runner, String script) throws Exception {
        runScript(getApiClient(), getOptions(), script, runner);
    }

    public static void runScript(ApiClientBase api, ScriptMainOptionsBase options, String script, ApiRunner runner) throws Exception {
        if (options.hasScriptVars()) {
            script = HandlebarsUtil.apply(runner.getHandlebars(), script, options.getScriptVars(),
                    options.getParamStartDelim(), options.getParamEndDelim());
        }
        runner.run(script);
    }

    @Override public void cleanup () {
        final ApiClientBase api = getApiClient();
        if (api != null && clockAdjusted) {
            try {
                resetClock();
            } catch (Exception e) {
                err("cleanup: error resetting server clock: "+e);
            }
        }
    }

    protected ScriptListener getScriptListener() {
        final OPT options = getOptions();
        return new ScriptListener(getClass().getName(), options.isSkipAllChecks(), options.isIncludeMailbox()) {

            public JsEngine jsEngine = getJsEngine();
            public Long clockMockBeforeTemp = null;

            @Override public void setCtxVars(Map<String, Object> ctx) {
                setScriptContextVars(ctx);
            }

            @Override public void beforeScript(String before, Map<String, Object> ctx) throws Exception {
                // ensure the environment is in the context, prefixed with "env."
                for (Map.Entry<String, String> env : System.getenv().entrySet()) {
                    final String envKey = "env_" + env.getKey();
                    if (!ctx.containsKey(envKey)) {
                        ctx.put(envKey, env.getValue());
                    }
                }

                final ApiRunnerListener handler = options.getBeforeHandlerObject();
                if (handler != null) {
                    handler.beforeScript(before, ctx);
                } else {
                    if (empty(before)) return;
                    if (before.startsWith(SLEEP)) {
                        handleSleep(before);
                    } else {
                        commonScript(before, ctx);
                    }
                }
            }

            @Override public void beforeCall(ApiScript script, Map<String, Object> ctx) {
                final ApiRunnerListener handler = options.getBeforeHandlerObject();
                if (handler != null) handler.beforeCall(script, ctx);
            }

            @Override public void afterScript(String after, Map<String, Object> ctx) throws Exception {
                final ApiRunnerListener handler = options.getAfterHandlerObject();
                if (handler != null) {
                    handler.afterScript(after, ctx);
                } else {
                    if (empty(after)) return;
                    if (after.startsWith(SLEEP)) {
                        handleSleep(after);

                    } else if (after.startsWith(AFTER_SAVE_DOWNLOAD)) {
                        if (after.equals(AFTER_SAVE_DOWNLOAD)) {
                            saveFile(getOptions().getTempDir(), ctx);
                        } else {
                            final RestResponse response = (RestResponse) ctx.get(ApiRunner.CTX_RESPONSE);
                            final String savePath = after.substring(AFTER_SAVE_DOWNLOAD.length()).trim();
                            if (savePath.equals("-")) {
                                @Cleanup final ByteArrayInputStream in = getResponseStream(response);
                                copyLarge(in, System.out);
                            } else {
                                saveFile(response, new File(savePath));
                            }
                        }
                    } else {
                        commonScript(after, ctx);
                    }
                }
            }

            public void commonScript(String after, Map<String, Object> ctx) throws Exception {
                if (after.startsWith("run-java-method ")) {
                    // A way to run Java inside run after (which are run inside Java ...)
                    runJavaMethod(after);
                } else if (after.startsWith("run-bean-method ")) {
                    runBeanMethod(after);
                } else if (after.startsWith("mock-system-clock-start ")) {
                    mockSystemTime(after, ctx);
                } else if (after.equals("mock-system-clock-end")) {
                    // Dingle end ends all the mock-system-clock-start that are set (as only the latest one is active).
                    resetClock();

                } else if (after.startsWith("delay ")) {
                    sleep(parseDuration(after.split(" ")[1]), "Delay");
                }
            }

            @Override public void afterCall(ApiScript script, Map<String, Object> ctx, RestResponse response) {
                final ApiRunnerListener handler = options.getAfterHandlerObject();
                if (handler != null) handler.afterCall(script, ctx, response);
            }

            protected void mockSystemTime(String scriptCmd, Map<String, Object> ctx) throws Exception {
                // Note that in case more than one mock-system-clock-start exists (without any mock-system-clock-end), the
                // latest one will be active.
                if (scriptCmd.startsWith("temporal-mock-system-clock ")) clockMockBeforeTemp = getSystemTimeOffset();
                final long offset = parseOffset(clockMockBeforeTemp, scriptCmd, ctx, jsEngine);
                final long start = realNow();
                final String timeEndpoint = getTimeEndpoint();
                if (timeEndpoint == null) die("mockSystemTime: no time endpoint defined");
                final RestResponse remoteTimeSetResponse = getApiClient().post(timeEndpoint, "\"=" + offset + "\"");
                setSystemTimeOffset(offset + (realNow() - start) / 2); // try to keep clocks close, assuming roundtrip was even on both sides
                out("mockSystemTime: now=" + new Date(now()) + ", remote=" + remoteTimeSetResponse.shortString());
                clockAdjusted = true;
            }

            private void runJavaMethod(String script) throws Exception {
                if (getDebugEndpoint() == null) die("runJavaMethod: no debug endpoint defined");
                getApiClient().post(getDebugEndpoint()+"/run/java-method?script="+urlEncode(script), null);
            }

            private void runBeanMethod(String script) throws Exception {
                if (getDebugEndpoint() == null) die("runJavaMethod: no debug endpoint defined");
                getApiClient().post(getDebugEndpoint()+"/run/bean-method?script="+urlEncode(script), null);
            }
        };
    }

    protected void setScriptContextVars(Map<String, Object> ctx) {}

    public static void handleSleep(String arg) {
        final String duration = arg.substring(SLEEP.length() + 1);
        final long sleepTime = parseDuration(duration);
        log.info("handleSleep: sleeping for "+duration + "("+sleepTime+" ms)");
        sleep(sleepTime);
    }

    public static long parseOffset(Long startingOffset, String scriptCmd, Map<String, Object> ctx, JsEngine jsEngine) {
        final long startOffset = startingOffset == null ? getSystemTimeOffset() : startingOffset;
        long offset;
        final String[] args = scriptCmd.split(" ");
        if (args[1].equals("set_days")) {
            // Support adding days to the current system time this way.
            offset = Long.parseLong(args[2]) * DAY;

        } else if (args[1].equals("add_days")) {
            // Support adding days to the current system time this way.
            offset = startOffset + Long.parseLong(args[2]) * DAY;

        } else if (args[1].equals("epoch")) {
            final String code = arrayToString(slice(args, 2, args.length), " ", "null", false);
            ctx.put("days", TimeUnit.DAYS.toMillis(1));
            ctx.put("hours", TimeUnit.HOURS.toMillis(1));
            ctx.put("minutes", TimeUnit.MINUTES.toMillis(1));
            ctx.put("seconds", TimeUnit.SECONDS.toMillis(1));
            offset = jsEngine.evaluateLong(code, ctx) - realNow();

        } else {
            // Support setting timestamp instead of the current system time.
            offset = startOffset + Long.parseLong(args[1]) - realNow();
        }
        return offset;
    }

    protected String getDebugEndpoint() { return null; }
    protected String getTimeEndpoint() { return null; }
    protected JsEngine getJsEngine() { return new StandardJsEngine(); }

    public void resetClock() throws Exception {
        setSystemTimeOffset(0);
        if (getTimeEndpoint() != null) getApiClient().delete(getTimeEndpoint());
        clockAdjusted = false;
    }

    protected ApiRunner getApiRunner() {
        final ApiClientBase api = getApiClient();
        api.setCaptureHeaders(getOptions().isCaptureHeaders());
        return new ApiRunner(api, getScriptListener()).setIncludeHandler(this); }

    @Override public String include(String path) {
        final OPT options = getOptions();
        final String envInclude = getPathEnvVar() == null ? null : System.getenv(getPathEnvVar());
        final File includeBase = options.hasIncludeBaseDir()
                ? options.getIncludeBaseDir() // use option if available
                : !empty(envInclude)
                ? new File(envInclude)    // otherwise use env var if available
                : options.hasScripts()
                ? options.getScripts()[0].getParentFile() // otherwise use first script dir
                : null;
        return includeFile(path, includeBase);
    }

    public String getPathEnvVar() { return null; }

    public static String includeFile(String path, File includeBase) {
        if (path.startsWith("/")) {
            final File absFile = new File(path);
            return absFile.exists()
                    ? FileUtil.toStringOrDie(absFile)
                    : ZillaRuntime.die("include: absolute path does not exist: "+path);
        }
        if (includeBase == null) return ZillaRuntime.die("include: include base directory could not be determined. use "+OPT_INCLUDE_BASEDIR+"/"+LONGOPT_INCLUDE_BASEDIR);
        if (!includeBase.exists() || !includeBase.isDirectory()) return ZillaRuntime.die("include: include base directory does not exist or is not a directory: "+abs(includeBase));
        final File includeFile = new File(abs(includeBase + File.separator + path + ".json"));
        return includeFile.exists() && includeFile.canRead()
                ? FileUtil.toStringOrDie(includeFile)
                : ZillaRuntime.die("include: include file does not exist or is unreadable: "+abs(includeFile));
    }

    public static void saveFile(File tempDir, Map<String, Object> ctx) throws Exception {
        final RestResponse response = (RestResponse) ctx.get(ApiRunner.CTX_RESPONSE);
        final String disposition = response.header("content-disposition");
        if (empty(disposition)) ZillaRuntime.die("saveFile: no content-disposition header");
        final String fileName = disposition.substring(disposition.indexOf("=")+1).replace("\"", "").replaceAll("\\s+", "_");
        final File file = new File(tempDir, fileName);
        saveFile(response, file);
    }

    public static void saveFile(RestResponse response, File file) throws IOException {
        @Cleanup final FileOutputStream out = new FileOutputStream(file);
        @Cleanup final ByteArrayInputStream in = getResponseStream(response);
        copy(in, out);
        out("saveFile: "+abs(file));
    }

    public static ByteArrayInputStream getResponseStream(RestResponse response) {
        if (response.bytes != null) {
            return new ByteArrayInputStream(response.bytes);
        } else if (response.json != null) {
            return new ByteArrayInputStream(response.json.getBytes());
        } else {
            return ZillaRuntime.die("saveFile: no data found in response.bytes nor response.json");
        }
    }

    public static class ScriptListener extends ApiRunnerListenerBase {
        private boolean skipAll;
        private boolean includeMailbox;

        public ScriptListener(String name, boolean skipAll, boolean includeMailbox) {
            super(name);
            this.skipAll = skipAll;
            this.includeMailbox = includeMailbox;
        }

        public ScriptListener (ScriptListener other) { super(other.getName()); copy(this, other); }

        @Override public void beforeCall(ApiScript script, Map<String, Object> ctx) { BaseMain.out(script.getComment()); }

        @Override public void afterCall(ApiScript script, Map<String, Object> ctx, RestResponse response) { MainBase.out(response); }

        @Override public boolean skipCheck(ApiScript script, ApiScriptResponseCheck check) {
            return skipAll || ((!includeMailbox) && check.getCondition().contains("mailbox."));
        }

        @Override public void unexpectedResponse(ApiScript script, RestResponse restResponse) {
            final String msg;
            switch (restResponse.status) {
                case HttpStatusCodes.UNPROCESSABLE_ENTITY:
                    msg = "Invalid: "+restResponse.json;
                    break;
                case HttpStatusCodes.NOT_FOUND:
                       msg = "Not Found: "+restResponse.json;
                    break;
                case HttpStatusCodes.FORBIDDEN:
                    msg = "Forbidden";
                    break;
                default:
                    msg = "unexpectedResponse (script="+script+"): "+restResponse;
                    break;
            }
            out(msg);
            log.error(msg);
        }
    }

}
