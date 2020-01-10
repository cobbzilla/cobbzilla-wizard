package org.cobbzilla.wizard.client.script;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.jknack.handlebars.Handlebars;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.handlebars.HandlebarsUtil;
import org.cobbzilla.util.javascript.StandardJsEngine;
import org.cobbzilla.wizard.client.ApiClientBase;

import java.util.HashMap;
import java.util.Map;

import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.reflect.ReflectionUtil.instantiate;
import static org.cobbzilla.util.string.StringUtil.ellipsis;
import static org.cobbzilla.util.time.TimeUtil.parseDuration;

@Slf4j @Accessors(chain=true)
public class ApiScript {

    public static final String DEFAULT_SESSION_NAME = "default";

    @Getter @Setter private String comment;
    public boolean hasComment () { return !empty(comment); }

    public static final String INCLUDE_DEFAULTS = "_defaults";
    public static final String PARAM_REQUIRED = "_required";
    @Getter @Setter private String include;
    public boolean hasInclude () { return !empty(include); }
    @JsonIgnore public boolean isIncludeDefaults () { return hasInclude() && getInclude().equals(INCLUDE_DEFAULTS); }

    @Getter @Setter private Map<String, Object> params;
    public boolean hasParams () { return !empty(params); }
    public void setParam(String name, Object value) {
        if (params == null) params = new HashMap<>();
        params.put(name, value);
    }
    public void addParams(Map<String, Object> params) {
        if (params == null) params = new HashMap<>();
        this.params.putAll(params);
    }

    @Getter @Setter private char paramStartDelim = '<';
    @Getter @Setter private char paramEndDelim = '>';

    @Getter @Setter private String onlyIf;
    public boolean hasOnlyIf () { return onlyIf != null; }

    @Getter @Setter private String unless;
    public boolean hasUnless () { return unless != null; }

    @Getter @Setter private String delay;
    public boolean hasDelay () { return !empty(delay); }
    @JsonIgnore public long getDelayMillis () { return parseDuration(delay); }

    @Getter @Setter private String before;
    public boolean hasBefore () { return !empty(before); }

    @Getter @Setter private String after;
    public boolean hasAfter () { return !empty(after); }

    @Getter @Setter private String timeout;
    @JsonIgnore public long getTimeoutMillis () { return parseDuration(timeout); }

    @Getter @Setter private NamedApiConnectionInfo connection;
    public boolean hasConnection () { return connection != null;
}
    @Getter @Setter private ApiScriptRequest request;

    @Getter @Setter private ApiScriptResponse response;
    public boolean hasResponse () { return response != null; }
    @JsonIgnore public String getRequestLine () { return request.getMethod() + " " + request.getUri(); }

    @Getter @Setter private long start = now();
    @JsonIgnore public long getAge () { return now() - start; }

    @Getter @Setter private ApiInnerScript nested;
    public boolean hasNested() { return nested != null && nested.hasScripts(); }

    @JsonIgnore public boolean isTimedOut() { return getAge() > getTimeoutMillis(); }

    @Override public String toString() {
        return "{\n" +
                "  \"comment\": \"" + comment + "\"," +
                "  \"request\": \"" + ellipsis(json(request), 300) + "\",\n" +
                "  \"response\": \"" + ellipsis(json(response), 300) + "\",\n" +
                "}";
    }

    public ApiClientBase getConnection(ApiClientBase defaultApi,
                                       ApiClientBase currentApi,
                                       Map<String, ApiClientBase> alternateApis,
                                       Handlebars handlebars,
                                       Map<String, Object> ctx) {

        if (!hasConnection()) return currentApi != null ? currentApi : defaultApi;
        final NamedApiConnectionInfo conn = HandlebarsUtil.applyReflectively(handlebars, getConnection(), ctx);
        ApiClientBase alternate;

        if (conn.hasBaseUri()) {
            if (conn.hasName()) {
                alternate = alternateApis.get(conn.getName());
                if (alternate != null && !alternate.getConnectionInfo().equals(conn)) {
                    log.warn("getConnection: redefining connection: "+ conn.getName());
                }
                try {
                    alternate = instantiate(defaultApi.getClass(), conn);
                } catch (Exception e) {
                    try {
                        alternate = instantiate(defaultApi.getClass());
                        alternate.setConnectionInfo(conn);
                    } catch (Exception e2) {
                        return die("getConnection: error instantiating new connection of type "+defaultApi.getClass()+": "+e);
                    }
                }
                alternateApis.put(conn.getName(), alternate);
                return alternate;

            } else {
                log.warn("getConnection: using anonymous connection: "+ conn);
                return new ApiClientBase(conn);
            }

        } else {
            if (!conn.hasName()) return die("getConnection: neither name nor baseUri was provided");
            if (conn.getName().equals(DEFAULT_SESSION_NAME)) {
                return defaultApi;
            }
            alternate = alternateApis.get(conn.getName());

            if (alternate == null) return die("getConnection: connection named '"+conn.getName()+"' not found");
            return alternate;
        }
    }

    public boolean shouldSkip(StandardJsEngine js, Handlebars handlebars, Map<String, Object> ctx) {
        if (hasOnlyIf()) {
            try {
                final String onlyIf = HandlebarsUtil.apply(handlebars, getOnlyIf(), ctx);
                if (js.evaluateBoolean(onlyIf, ctx, false)) {
                    log.info("onlyIf '"+onlyIf+"' returned true, NOT skipping script");
                    return false;
                } else {
                    log.info("onlyIf '"+onlyIf+"' returned false, skipping script");
                    return true;
                }
            } catch (Exception e) {
                return die("runOnce: error evaluating onlyIf '"+getOnlyIf()+"': "+e);
            }
        }
        if (hasUnless()) {
            try {
                final String unless = HandlebarsUtil.apply(handlebars, getUnless(), ctx);
                if (js.evaluateBoolean(unless, ctx, false)) {
                    log.info("unless '"+unless+"' returned true, skipping script");
                    return true;
                } else {
                    log.info("unless '"+unless+"' returned false, NOT skipping script");
                    return false;
                }
            } catch (Exception e) {
                return die("runOnce: error evaluating unless '"+getUnless()+"': "+e);
            }
        }
        return false;
    }
}
