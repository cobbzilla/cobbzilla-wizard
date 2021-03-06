package org.cobbzilla.wizard.client.script;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.jknack.handlebars.Handlebars;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.impl.client.HttpClientBuilder;
import org.cobbzilla.util.handlebars.HandlebarsUtil;
import org.cobbzilla.util.http.HttpRequestBean;
import org.cobbzilla.util.http.HttpResponseBean;
import org.cobbzilla.util.http.HttpUtil;
import org.cobbzilla.util.javascript.StandardJsEngine;
import org.cobbzilla.wizard.client.ApiClientBase;

import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Map;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.http.HttpSchemes.isHttpOrHttps;
import static org.cobbzilla.util.json.JsonUtil.*;
import static org.cobbzilla.util.system.Sleep.sleep;
import static org.cobbzilla.util.time.TimeUtil.parseDuration;
import static org.cobbzilla.wizard.client.script.ApiRunner.standardHandlebars;
import static org.cobbzilla.wizard.main.ScriptMainBase.SLEEP;
import static org.cobbzilla.wizard.main.ScriptMainBase.handleSleep;

@Slf4j
public class SimpleApiRunnerListener extends ApiRunnerListenerBase {

    public static final String ADJUST_SYSTEM_CLOCK = "adjust_system_clock";
    public static final String RESET_SYSTEM_CLOCK = "reset_system_clock";
    public static final String AWAIT_URL = "await_url";
    public static final String VERIFY_UNREACHABLE = "verify_unreachable";
    public static final String RESPONSE_VAR = "await_json";
    public static final String ECHO_IN_LOG = "echo_in_log ";
    public static final String ADD_TO_CTX = "add_to_ctx ";

    public static final long DEFAULT_AWAIT_URL_CHECK_INTERVAL = SECONDS.toMillis(10);
    public static final long DEFAULT_VERIFY_UNAVAILABLE_TIMEOUT = SECONDS.toMillis(10);

    private static final char GRACE_AND_TIMEOUT_SEPARATOR = ':';

    public SimpleApiRunnerListener(String name) { super(name); }

    private ApiClientBase currentApi() { return getApiRunner().getCurrentApi(); }

    @Getter(lazy=true) private final Handlebars handlebars = initHandlebars();
    protected Handlebars initHandlebars() { return standardHandlebars(new Handlebars(new HandlebarsUtil(getClass().getSimpleName()))); }

    @Override public void beforeScript(String before, Map<String, Object> ctx) throws Exception {
        if (before == null) return;
        if (before.startsWith(SLEEP)) {
            handleSleep(before);
        } else if (before.startsWith(ADJUST_SYSTEM_CLOCK)) {
            final String duration = before.substring(ADJUST_SYSTEM_CLOCK.length()).trim();
            setSystemTimeOffset(parseDuration(duration));

        } else if (before.startsWith(RESET_SYSTEM_CLOCK)) {
            setSystemTimeOffset(0);
        } else if (before.startsWith(AWAIT_URL)) {
            handleAwaitUrl(before, ctx);
        } else if (before.startsWith(VERIFY_UNREACHABLE)) {
            handleVerifyUnreachable(before, ctx);
        } else if (before.startsWith(ECHO_IN_LOG)) {
            handleEcho(before, ctx);
        } else if (before.startsWith(ADD_TO_CTX)) {
            handleAddToCtx(before, ctx);
        } else {
            super.beforeScript(before, ctx);
        }
    }

    @Override public void afterScript(String after, Map<String, Object> ctx) throws Exception {
        if (after == null) return;
        if (after.startsWith(SLEEP)) {
            handleSleep(after);
        } else if (after.startsWith(ADJUST_SYSTEM_CLOCK)) {
            final String duration = after.substring(ADJUST_SYSTEM_CLOCK.length()).trim();
            setSystemTimeOffset(parseDuration(duration));
        } else if (after.startsWith(RESET_SYSTEM_CLOCK)) {
            setSystemTimeOffset(0);
        } else if (after.startsWith(AWAIT_URL)) {
            handleAwaitUrl(after, ctx);
        } else if (after.startsWith(VERIFY_UNREACHABLE)) {
            handleVerifyUnreachable(after, ctx);
        } else if (after.startsWith(ECHO_IN_LOG)) {
            handleEcho(after, ctx);
        } else if (after.startsWith(ADD_TO_CTX)) {
            handleAddToCtx(after, ctx);
        } else {
            super.afterScript(after, ctx);
        }
    }

    private StandardJsEngine js = new StandardJsEngine();

    // todo: allow listeners to have access to the runner, or at least the current/correct API
    // we are getting 404 because we're sending the wrong token (default API instead of remote)
    private boolean handleAwaitUrl(String arg, Map<String, Object> ctx) {
        final String[] parts = HandlebarsUtil.apply(getHandlebars(), arg, ctx).split("\\s+");
        if (parts.length < 3) return die(AWAIT_URL+": no URL and/or timeout specified");
        final String url = formatUrl(parts[1]);
        final long timeout = waitAndParseTimeout(parts[2]);
        final long checkInterval = (parts.length >= 4) ? parseDuration(parts[3]) : DEFAULT_AWAIT_URL_CHECK_INTERVAL;
        final String jsCondition = (parts.length >= 5) ? parseJs(parts, 4) : null;

        final long start = now();
        final HttpRequestBean request = formatRequest(new HttpRequestBean(url));
        while (now() - start < timeout) {
            try {
                final HttpResponseBean response = HttpUtil.getResponse(request);
                if (!response.isOk()) {
                    log.info(AWAIT_URL + ": received HTTP status " + response.getStatus() + " (will retry): " + url);
                } else if (empty(jsCondition)) {
                    log.info(AWAIT_URL + ": received HTTP status OK and there's no JS condition: " + url);
                    return true;
                } else {
                    ctx.put(RESPONSE_VAR, toResponseObject(response));
                    if (js.evaluateBoolean(jsCondition, ctx, false)) {
                        log.info(AWAIT_URL + ": received HTTP status OK and JS condition was true ("
                                 + jsCondition + "): " + url);
                        return true;
                    } else {
                        log.info(AWAIT_URL + ": received HTTP status OK but JS condition was false ("
                                  + jsCondition + "): (will retry): " + url);
                    }
                }
            } catch (Exception e) {
                log.warn(AWAIT_URL+": error, will retry: "+e);
            }
            sleep(checkInterval, AWAIT_URL+" "+url);
        }
        return die(AWAIT_URL+": timeout waiting for "+url);
    }

    private long waitAndParseTimeout(@NonNull final String values) {
        // grace period of not doing anything may be set up within this option as <grace>.<timeout>. If there's no dot
        // there, then only <timeout> is given, and there's not waiting on grace period to do here.
        final var separatorPosition = values.indexOf(GRACE_AND_TIMEOUT_SEPARATOR);
        if (separatorPosition < 0) return parseDuration(values);

        final var graceStr = values.substring(0, separatorPosition);
        final var grace = parseDuration(graceStr);
        log.info(AWAIT_URL + ": sleeping for grace period before checking next awaiting URL: " + graceStr);
        sleep(grace, AWAIT_URL + " -grace-");

        return parseDuration(values.substring(separatorPosition + 1));
    }

    private boolean handleVerifyUnreachable(String arg, Map<String, Object> ctx) {
        final String[] parts = arg.split("\\s+");
        if (parts.length < 2) return die(VERIFY_UNREACHABLE+": no URL specified");
        final String url = formatUrl(parts[1]);
        final long connectTimeout = (parts.length >= 3) ? parseDuration(parts[2]) : DEFAULT_VERIFY_UNAVAILABLE_TIMEOUT;
        final long socketTimeout = (parts.length >= 4) ? parseDuration(parts[3]) : connectTimeout;

        final RequestConfig.Builder requestBuilder = RequestConfig.custom()
                .setConnectTimeout((int) connectTimeout)
                .setSocketTimeout((int) socketTimeout)
                .setConnectionRequestTimeout((int) Math.max(connectTimeout, socketTimeout));
        final HttpClient client = HttpClientBuilder.create()
                .setDefaultRequestConfig(requestBuilder.build())
                .build();
        final HttpRequestBean request = formatRequest(new HttpRequestBean(url));
        try {
            final HttpResponseBean response = HttpUtil.getResponse(request, client);
            return die(VERIFY_UNREACHABLE + ": URL was reachable (HTTP status " + response.getStatus() + ") : " + request.getUri());
        } catch (SocketException e) {
            if (e instanceof HttpHostConnectException || e.getMessage().contains(" unreachable ")) {
                log.info(VERIFY_UNREACHABLE+": OK: "+e);
            } else {
                log.info(VERIFY_UNREACHABLE+": unreachable? "+e);
            }
            return true;
        } catch (UnknownHostException e) {
            log.info(VERIFY_UNREACHABLE+": OK: "+e);
            return true;
        } catch (Exception e) {
            log.info(VERIFY_UNREACHABLE+": unreachable? "+e);
            return true;
        }
    }

    @NonNull private String handleEcho(@NonNull final String arg, @NonNull final Map<String, Object> ctx) {
        final var parts = arg.split("\\s+", 2);
        if (parts.length != 2) return die(ECHO_IN_LOG + ": no variables specified");
        final var output = HandlebarsUtil.apply(getHandlebars(), parts[1], ctx);
        log.info("ECHO:\n" + output);
        return output;
    }

    @NonNull private Map<String, Object> handleAddToCtx(@NonNull final String arg,
                                                        @NonNull final Map<String, Object> ctx) {
        final var parts = arg.split("\\s+", 2);
        if (parts.length != 2) return die(ADD_TO_CTX + ": no variables specified");
        ctx.putAll(fromJsonOrDie(parts[1], Map.class));
        return ctx;
    }

    private String formatUrl(String url) {
        final ApiClientBase currentApi = currentApi();
        if (!isHttpOrHttps(url)) {
            if (!url.startsWith("/") && !currentApi.getBaseUri().endsWith("/")) url = "/" + url;
            url = currentApi.getBaseUri() + url;
        }
        return url;
    }

    private Object toResponseObject(HttpResponseBean response) {
        final String entityTypeHeaderValue = response.getFirstHeaderValue(currentApi().getEntityTypeHeaderName());
        final Class<?> responseClass = ApiRunner.getResponseObjectClass(entityTypeHeaderValue, JsonNode.class);
        try {
            return json(response.getEntityString(), responseClass);
        } catch (Exception e) {
            log.warn("toResponseObject: error parsing response as "+responseClass+", returning as-is");
            return response.getEntityString();
        }
    }

    private String parseJs(String[] parts, int startIndex) {
        final StringBuilder b = new StringBuilder();
        for (int i=startIndex; i<parts.length; i++) {
            b.append(parts[i]).append(" ");
        }
        return b.toString();
    }

    protected HttpRequestBean formatRequest(HttpRequestBean request) {
        final ApiClientBase currentApi = currentApi();
        if (request.getUri().startsWith(currentApi.getBaseUri())) {
            request.setHeader(currentApi.getTokenHeader(), currentApi.getToken());
        }
        return request;
    }

}
