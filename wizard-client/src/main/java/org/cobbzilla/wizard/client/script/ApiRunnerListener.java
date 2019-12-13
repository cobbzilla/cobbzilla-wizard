package org.cobbzilla.wizard.client.script;

import org.cobbzilla.wizard.util.RestResponse;

import java.util.Map;

public interface ApiRunnerListener {

    String getName();
    ApiRunner getApiRunner();
    void setApiRunner(ApiRunner runner);

    default void setCtxVars(Map<String, Object> ctx) {}

    void beforeScript(String before, Map<String, Object> ctx) throws Exception;
    void afterScript(String after, Map<String, Object> ctx) throws Exception;

    void beforeCall(ApiScript script, Map<String, Object> ctx);
    void afterCall(ApiScript script, Map<String, Object> ctx, RestResponse response);

    void statusCheckFailed(ApiScript script, String uri, RestResponse restResponse);
    void conditionCheckFailed(String message, ApiScript script, RestResponse restResponse, ApiScriptResponseCheck check,
                              Map<String, Object> ctx);

    void sessionIdNotFound(ApiScript script, RestResponse restResponse);

    void scriptCompleted(ApiScript script);
    void scriptTimedOut(ApiScript script);
    void unexpectedResponse(ApiScript script, RestResponse restResponse);

    boolean skipCheck(ApiScript script, ApiScriptResponseCheck check);

}