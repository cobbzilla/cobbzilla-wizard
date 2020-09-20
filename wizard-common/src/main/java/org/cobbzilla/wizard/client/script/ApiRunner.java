package org.cobbzilla.wizard.client.script;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.jknack.handlebars.Handlebars;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.HttpClient;
import org.apache.http.client.protocol.HttpClientContext;
import org.cobbzilla.util.collection.NameAndValue;
import org.cobbzilla.util.handlebars.HandlebarsUtil;
import org.cobbzilla.util.handlebars.SimpleJurisdictionResolver;
import org.cobbzilla.util.http.HttpMethods;
import org.cobbzilla.util.http.HttpStatusCodes;
import org.cobbzilla.util.io.FileUtil;
import org.cobbzilla.util.javascript.StandardJsEngine;
import org.cobbzilla.util.string.StringUtil;
import org.cobbzilla.wizard.client.ApiClientBase;
import org.cobbzilla.wizard.util.RestResponse;
import org.cobbzilla.wizard.util.TestNames;
import org.cobbzilla.wizard.validation.ConstraintViolationBean;
import org.cobbzilla.wizard.validation.ValidationErrors;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.apache.http.HttpHeaders.CONTENT_TYPE;
import static org.apache.http.entity.ContentType.MULTIPART_FORM_DATA;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.http.HttpStatusCodes.OK;
import static org.cobbzilla.util.json.JsonUtil.*;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;
import static org.cobbzilla.util.reflect.ReflectionUtil.forName;
import static org.cobbzilla.util.string.StringUtil.trimQuotes;
import static org.cobbzilla.util.system.Sleep.sleep;
import static org.cobbzilla.wizard.client.script.ApiScript.DEFAULT_SESSION_NAME;
import static org.cobbzilla.wizard.client.script.ApiScript.PARAM_REQUIRED;

@NoArgsConstructor @Accessors(chain=true) @Slf4j
public class ApiRunner {

    public static final String CTX_JSON = "json";
    public static final String CTX_RESPONSE = "response";
    public static final String NEW_SESSION = "new";
    public static final String TEMPORAL_NEW_SESSION = "temp-new";

    private StandardJsEngine js = new StandardJsEngine();

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection") // intended for use in debugging
    @Getter private static Map<String, ApiScript> currentScripts = new HashMap<>();
    public static void resetScripts () { currentScripts.clear(); }

    public ApiRunner(ApiClientBase api, ApiRunnerListener listener) {
        this.api = api;
        this.listener = listener;
        this.listener.setApiRunner(this);
    }

    public ApiRunner(ApiRunner other, HttpClient httpClient) {
        copy(this, other);
        this.api = other.api.copy();
        this.api.setHttpClient(httpClient);
        this.api.setHttpContext(HttpClientContext.create());
        this.listener = copy(other.listener);
        this.listener.setApiRunner(this);
        this.ctx.putAll(other.getContext());
    }

    public ApiRunner(StandardJsEngine js, ApiClientBase api, ApiRunnerListener listener, ApiScriptIncludeHandler includeHandler) {
        this.js = js;
        this.api = api;
        this.listener = listener;
        this.listener.setApiRunner(this);
        this.includeHandler = includeHandler;
    }

    public void setScriptForThread(ApiScript script) {
        if (script.hasComment()) log.info(script.getComment());
        currentScripts.put(Thread.currentThread().getName(), script);
    }

    // be careful - if you have multiple ApiRunners in the same classloader, these methods will not be useful
    // intended for convenience in the common case of a single ApiRunner
    public static ApiScript script() { return currentScripts.isEmpty() ? null : currentScripts.values().iterator().next(); }
    public static String comment() { return currentScripts.isEmpty() ? null : currentScripts.values().iterator().next().getComment(); }

    @Getter private ApiClientBase api;
    @Getter private ApiClientBase currentApi;

    private Map<String, ApiClientBase> alternateApis = new HashMap<>();

    private ApiRunnerListener listener = new ApiRunnerListenerBase("default");
    @Getter @Setter private ApiScriptIncludeHandler includeHandler = new ApiScriptIncludeHandlerBase();

    protected final Map<String, Object> ctx = new ConcurrentHashMap<>();
    public Map<String, Object> getContext () { return ctx; }

    @Getter(lazy=true) private final Handlebars handlebars = standardHandlebars(new Handlebars(new HandlebarsUtil("api-runner(" + api + ")")));

    public static Handlebars standardHandlebars(Handlebars hbs) {
        HandlebarsUtil.registerUtilityHelpers(hbs);
        HandlebarsUtil.registerDateHelpers(hbs);
        HandlebarsUtil.registerCurrencyHelpers(hbs);
        HandlebarsUtil.registerJavaScriptHelper(hbs, StandardJsEngine::new);
        HandlebarsUtil.registerJurisdictionHelpers(hbs, SimpleJurisdictionResolver.instance);
        return hbs;
    }

    protected final Map<String, Class> storeTypes = new HashMap<>();
    protected final Map<String, String> namedSessions = new HashMap<>();
    public void addNamedSession (String name, String token) { namedSessions.put(name, token); }

    public void reset () {
        ctx.clear();
        storeTypes.clear();
        api.logout();
    }

    public ApiScript[] include (ApiScript script) {
        final String includePath = handlebars(script.getInclude(), getContext());
        if (getIncludeHandler() != null) {
            final Map<String, Object> context = new HashMap<>(System.getenv());
            context.putAll(getContext());
            if (listener != null) listener.beforeCall(script, context);
            return jsonWithComments(handlebars(getIncludeHandler().include(includePath),
                    HandlebarsUtil.apply(getHandlebars(), script.getParams(), context),
                    script.getParamStartDelim(), script.getParamEndDelim()), ApiScript[].class);
        }
        return notSupported("include("+ includePath +"): no includeHandler set");
    }

    public void run(String script) throws Exception { run(jsonWithComments(script, ApiScript[].class)); }

    public boolean run(ApiScript[] scripts) throws Exception {
        boolean allSucceeded = true;
        for (ApiScript script : scripts) if (!run(script)) allSucceeded = false;
        return allSucceeded;
    }

    public boolean run(ApiScript script) throws Exception {
        if (listener != null) listener.setCtxVars(ctx);
        currentApi = script.getConnection(this.api, currentApi, alternateApis, getHandlebars(), ctx);
        if (script.shouldSkip(js, getHandlebars(), ctx)) return true;
        if (script.hasInclude()) {
            if (script.isIncludeDefaults()) return true; // skip this block. used in validation before running included script
            final String logPrefix = (script.hasComment() ? script.getComment()+"\n" : "") + ">>> ";
            log.info(logPrefix+"including script: '"+script.getInclude()+"'"+(script.hasParams()?" {"+ StringUtil.toString(NameAndValue.map2list(script.getParams()), ", ")+"}":""));
            ApiScript[] include = include(script);
            boolean paramsChanged = false;
            if (include.length > 0 && include[0].isIncludeDefaults()) {
                final ApiScript defaults = include[0];
                if (empty(defaults.getParams())) {
                    log.warn(logPrefix+"no default parameters set");
                } else {
                    @NonNull final var defaultParamsLog = new StringBuilder();
                    for (Map.Entry<String, Object> param : defaults.getParams().entrySet()) {
                        final String pName = param.getKey();
                        final Object pValue = param.getValue();
                        if (empty(pName)) return die(logPrefix+"empty default param name");
                        if (pValue != null && (!script.hasParams() || !script.getParams().containsKey(pName) || empty(script.getParams().get(pName)))) {
                            if ((pValue instanceof String) && pValue.equals(PARAM_REQUIRED)) {
                                return die(logPrefix+"required parameter is undefined: "+pName);
                            }
                            if ((pValue instanceof Boolean) && !((Boolean) pValue)) {
                                continue; // boolean values already default to false, no need to change script
                            }
                            defaultParamsLog.append("\n\t").append(pName).append('=').append(pValue);
                            script.setParam(pName, pValue);
                            paramsChanged = true;
                        }
                    }
                    if (defaultParamsLog.length() > 0) {
                        log.info(logPrefix + "Following parameter(s) are undefined, using shown default value(s):"
                                 + defaultParamsLog.toString());
                    }
                }
            }
            if (paramsChanged) include = include(script); // re-include because params have changed

            if (script.hasBefore() && listener != null) listener.beforeCall(script, getContext());
            final boolean ok = run(include);
            if (ok && script.hasAfter() && listener != null) listener.afterCall(script, getContext(), null);

            log.info(">>> included script completed: '"+script.getInclude()+"'"+(script.hasParams()?" {"+ StringUtil.toString(NameAndValue.map2list(script.getParams()), ", ")+"}":"")+", ok="+ok);
            return ok;

        } else {
            setScriptForThread(script);
            if (script.hasDelay()) sleep(script.getDelayMillis(), "delaying before starting script: " + script);
            if (listener != null) listener.beforeScript(script.getBefore(), getContext());
            try {
                script.setStart(now());
                do {
                    if (runOnce(script)) return true;
                    sleep(Math.min(script.getTimeoutMillis() / 10, 1000), "waiting to retry script: " + script);
                } while (!script.isTimedOut());
                if (listener != null) listener.scriptTimedOut(script);
                return false;

            } catch (Exception e) {
                log.warn("run(" + script + "): " + e, e);
                throw e;

            } finally {
                if (listener != null) listener.afterScript(script.getAfter(), getContext());
            }
        }
    }

    public boolean runOnce(ApiScript script) throws Exception {
        if (script.hasNested()) return runInner(script);

        final ApiScriptRequest request = script.getRequest();
        final String method = request.getMethod().toUpperCase();
        ctx.put("now", script.getStart());

        String oldSessionsId = null;
        final ApiClientBase api = currentApi;

        if (request.hasSession()) {
            if (request.getSession().equals(NEW_SESSION)) {
                api.logout();
            } else if (request.getSession().equals(TEMPORAL_NEW_SESSION)) {
                oldSessionsId = api.getToken();
                api.logout();
            } else {
                final String sessionId = namedSessions.get(request.getSession());
                if (sessionId == null) return die("Session named " + request.getSession() + " is not defined (" + namedSessions + ")");
                api.setToken(sessionId);
            }
        }

        if (request.hasHeaders()) api.setHeaders(request.getHeaders());
        boolean isCaptureHeaders = api.isCaptureHeaders();
        try {
            if (script.hasResponse() && script.getResponse().isRaw()) api.setCaptureHeaders(true);

            String uri = handlebars(request.getUri(), getContext());
            if (!uri.startsWith("/")) uri = "/" + uri;
            log.debug("runOnce: transformed uri from "+request.getUri()+" -> "+uri);

            boolean success = true;
            final RestResponse restResponse;

            if (listener != null) listener.beforeCall(script, getContext());
            switch (method) {
                case HttpMethods.GET:
                    restResponse = api.doGet(uri);
                    break;

                case HttpMethods.PUT:
                    restResponse = api.doPut(uri, subst(request));
                    api.removeHeaders();
                    break;

                case HttpMethods.POST:
                    if (request.hasHeaders() && request.hasHeader(CONTENT_TYPE)) {
                        if (!request.getHeader(CONTENT_TYPE).equals(MULTIPART_FORM_DATA.getMimeType())) {
                            return die("run("+script+"): invalid request content type");
                        }

                        final String filePath = request.getEntity().get("file").textValue();
                        File file;
                        if (filePath.startsWith("data:")) {
                            file = FileUtil.temp(".tmp");
                            FileUtil.toFile(file, handlebars(filePath.substring("data:".length()), ctx));
                        } else {
                            if (empty(filePath)) die("run(" + script + "): file path doesn't exist");
                            file = new File(filePath);
                            if (!file.exists()) die("run(" + script + "): file doesn't exist");
                        }

                        restResponse = api.doPost(uri, file);
                    } else {
                        restResponse = api.doPost(uri, subst(request));
                    }
                    api.removeHeaders();
                    break;

                case HttpMethods.DELETE:
                    restResponse = api.doDelete(uri);
                    break;

                default:
                    return die("run("+script+"): invalid request method: "+method);
            }
            if (listener != null) listener.afterCall(script, getContext(), restResponse);

            if (script.hasResponse()) {
                final ApiScriptResponse response = script.getResponse();

                if (!response.statusOk(restResponse.status)) {
                    if (listener != null) listener.statusCheckFailed(script, uri, restResponse);
                }

                final JsonNode responseEntity;
                if (response.hasType() && response.getType().equals(String.class.getName()) && !(restResponse.json.startsWith("\"") && restResponse.json.endsWith("\""))) {
                    restResponse.json = "\"" + restResponse.json + "\"";
                }
                responseEntity = empty(restResponse.json) || response.isRaw() ? null : json(restResponse.json, JsonNode.class);
                Object responseObject = responseEntity;

                if (response.getStatus() == HttpStatusCodes.UNPROCESSABLE_ENTITY) {
                    if (responseEntity != null) {
                        responseObject = new ValidationErrors(
                                Arrays.asList(fromJsonOrDie(responseEntity, ConstraintViolationBean[].class)));
                    }
                } else {
                    Class<?> storeClass = null;
                    if (response.hasType()) {
                        storeClass = forName(response.getType());
                        if (response.hasStore()) storeTypes.put(response.getStore(), storeClass);

                    } else if (response.hasStore()) {
                        storeClass = storeTypes.get(response.getStore());
                    }

                    // if HTTP header is telling us the type, try to use that
                    if (restResponse.hasHeader(api.getEntityTypeHeaderName())) {
                        String entityTypeHeaderValue = restResponse.header(api.getEntityTypeHeaderName());
                        storeClass = getResponseObjectClass(entityTypeHeaderValue, storeClass);
                        if (response.hasStore()) storeTypes.put(response.getStore(), storeClass);
                    }
                    if (response.isRaw()) {
                        if (response.hasStore()) {
                            storeTypes.put(response.getStore(), RestResponse.class);
                            ctx.put(response.getStore(), restResponse);
                        }
                    } else if (responseEntity != null) {
                        if (storeClass == null) {
                            if (responseEntity.isArray()) {
                                storeClass = Map[].class;
                            } else if (responseEntity.isObject()) {
                                storeClass = Map.class;
                            } else if (responseEntity.isTextual()) {
                                storeClass = String.class;
                            } else if (responseEntity.isIntegralNumber()) {
                                storeClass = Long.class;
                            } else if (responseEntity.isDouble()) {
                                storeClass = Double.class;
                            } else {
                                storeClass = JsonNode.class; // punt
                            }
                        }
                        try {
                            responseObject = fromJsonOrDie(responseEntity, storeClass);
                        } catch (IllegalStateException e) {
                            log.warn("runOnce: error parsing JSON: " + e);
                            responseObject = responseEntity;
                        }

                        if (response.hasStore()) ctx.put(response.getStore(), responseObject);

                        if (response.hasSession()) {
                            final JsonNode sessionIdNode;
                            if (response.getSession().equals(".")) {
                                sessionIdNode = responseEntity;
                            } else {
                                sessionIdNode = findNode(responseEntity, response.getSession());
                            }
                            if (sessionIdNode == null) {
                                if (listener != null) listener.sessionIdNotFound(script, restResponse);
                            } else {
                                final String sessionId = sessionIdNode.textValue();
                                if (empty(sessionId)) die("runOnce: empty sessionId: "+restResponse);
                                final String sessionName = response.hasSessionName() ? response.getSessionName() : DEFAULT_SESSION_NAME;
                                namedSessions.put(sessionName, sessionId);
                                api.setToken(sessionId);
                            }
                        }
                    }
                }

                ctx.put(CTX_RESPONSE, restResponse);

                if (response.hasChecks()) {
                    if (response.hasDelay()) sleep(response.getDelayMillis(), "runOnce: delaying "+response.getDelay()+" before checking response conditions");

                    final Map<String, Object> localCtx = new HashMap<>(getContext());
                    localCtx.put(CTX_JSON, responseObject);

                    for (ApiScriptResponseCheck check : response.getCheck()) {
                        if (listener != null && listener.skipCheck(script, check)) continue;
                        final String condition = handlebars(check.getCondition(), localCtx);
                        Boolean result = null;
                        long timeout = check.getTimeoutMillis();
                        long checkStart = now();
                        do {
                            try {
                                result = js.evaluateBoolean(condition, localCtx);
                                if (result) break;
                                if (script.isTimedOut()) {
                                    log.warn("runOnce("+script+"): condition check ("+condition+") returned false");
                                } else {
                                    log.debug("runOnce("+script+"): condition check ("+condition+") returned false");
                                }
                            } catch (Exception e) {
                                if (script.isTimedOut()) {
                                    log.warn("runOnce(" + script + "): condition check (" + condition + ") failed: " + e);
                                } else {
                                    log.debug("runOnce(" + script + "): condition check (" + condition + ") failed: " + e);
                                }
                            }
                            sleep(Math.min(timeout/10, 1000), "waiting to retry condition: "+condition);
                        } while (now() - checkStart < timeout);

                        if (result == null || !result) {
                            success = false;
                            final String msg = result == null ? "Exception in execution" : "Failed condition";
                            if (listener != null) listener.conditionCheckFailed(msg, script, restResponse, check, localCtx);
                        }
                    }
                }

            } else if (restResponse.status != OK) {
                success = false;
                if (listener != null) listener.unexpectedResponse(script, restResponse);
            }

            if (listener != null) listener.scriptCompleted(script);

            if (!empty(oldSessionsId)) api.setToken(oldSessionsId);

            return success;
        } finally {
            api.setCaptureHeaders(isCaptureHeaders);
        }
    }

    public static Class<?> getResponseObjectClass(String entityTypeHeaderValue, Class<?> currentClass) {
        if (!empty(entityTypeHeaderValue) && !entityTypeHeaderValue.equals("[]")) {
            if (entityTypeHeaderValue.contains("<") && entityTypeHeaderValue.endsWith(">")) {
                entityTypeHeaderValue = entityTypeHeaderValue.substring(0, entityTypeHeaderValue.indexOf("<"));
            }
            try {
                return forName(entityTypeHeaderValue);
            } catch (Exception e) {
                log.warn("runOnce: error instantiating type (will treat as JsonNode): " + entityTypeHeaderValue + ": " + e);
            }
        }
        return currentClass;
    }

    private boolean runInner(ApiScript script) throws Exception {
        final ApiInnerScript inner = script.getNested();
        inner.setParent(script);
        final List<ApiScript> scripts = inner.getAllScripts(js, getHandlebars(), getContext());
        final Map<String, String> failedParams = new LinkedHashMap<>();
        for (ApiScript s : scripts) {
            try {
                if (!run(s)) {
                    switch (inner.getRunMode()) {
                        case fail_fast: return false;
                        default: failedParams.put(json(s.getParams()), "(returned false)");
                    }
                }
            } catch (Exception e) {
                switch (inner.getRunMode()) {
                    case fail_fast: throw e;
                    default: failedParams.put(json(s.getParams()), e.getClass().getSimpleName()+": "+e.getMessage());
                }
            }
        }
        if (!empty(failedParams)) {
            switch (inner.getRunMode()) {
                case run_all: return die("runInner: failed iterations:\n"+StringUtil.toString(failedParams.keySet(), "\n"));
                default: return die("runInner: failed iterations:\n"+json(failedParams));
            }
        }
        return true;
    }

    protected String subst(ApiScriptRequest request) {
        String json = requestEntityJson(request);
        json = TestNames.replaceTestNames(json);
        json = trimQuotes(json);
        return json;
    }

    protected String requestEntityJson(ApiScriptRequest request) {
        final String json = request.getJsonEntity(getContext());
        return request.isHandlebarsEnabled() ? handlebars(json, getContext()) : json;
    }

    protected String scriptName(ApiScript script, String name) { return "api-runner(" + script + "):" + name; }

    protected String handlebars(String value, Map<String, Object> ctx) {
        return HandlebarsUtil.apply(getHandlebars(), value, mergeEnv(ctx));
    }

    protected String handlebars(String value, Map<String, Object> ctx, char altStart, char altEnd) {
        return HandlebarsUtil.apply(getHandlebars(), value, mergeEnv(ctx), altStart, altEnd);
    }

    private Map<String, Object> mergeEnv(Map<String, Object> ctx) {

        final Map<String, String> env = System.getenv();
        if (empty(ctx)) return empty(env) ? ctx : new HashMap<>(env);
        if (empty(env)) return ctx;

        final Map<String, Object> merged = new HashMap<>();
        if (!empty(env)) merged.putAll(env);
        if (!empty(ctx)) merged.putAll(ctx);

        return merged;
    }

}
