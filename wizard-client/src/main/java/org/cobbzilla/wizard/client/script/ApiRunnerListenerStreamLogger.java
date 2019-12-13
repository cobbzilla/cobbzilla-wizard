package org.cobbzilla.wizard.client.script;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.util.RestResponse;

import java.io.Writer;
import java.util.Map;

@Slf4j @AllArgsConstructor
public class ApiRunnerListenerStreamLogger implements ApiRunnerListener {

    @Getter private final String name;
    @Getter private final Writer out;
    @Getter @Setter private ApiRunner apiRunner;

    public ApiRunnerListenerStreamLogger(String name, Writer out) {
        this.name = name;
        this.out = out;
    }

    @Override public void beforeScript(String before, Map<String, Object> ctx) throws Exception {
        write("beforeScript / "+before);
    }

    @Override public void afterScript(String after, Map<String, Object> ctx) throws Exception {
        write("afterScript / " + after);
    }

    private void write(String s) { try { out.write(s); } catch (Exception e) { log.error("write: "+e, e); } }

    private String scriptInfo(ApiScript script) {
        return script.getRequestLine() + (script.hasComment() ? " / " + script.getComment() : "");
    }

    @Override public void beforeCall(ApiScript script, Map<String, Object> ctx) {
        write("beforeCall / " + scriptInfo(script));
    }

    @Override public void afterCall(ApiScript script, Map<String, Object> ctx, RestResponse response) {
        write("afterCall / " + scriptInfo(script));
    }

    @Override public void statusCheckFailed(ApiScript script, String uri, RestResponse restResponse) {
        write("statusCheckFailed / " + scriptInfo(script) + " / " + uri + " / " + restResponse);
    }

    @Override public void conditionCheckFailed(String message, ApiScript script, RestResponse restResponse, ApiScriptResponseCheck check, Map<String, Object> ctx) {
        write("conditionCheckFailed / " + scriptInfo(script) + " / " + message + " / " + restResponse + " / " + check);
    }

    @Override public void sessionIdNotFound(ApiScript script, RestResponse restResponse) {
        write("sessionIdNotFound / " + scriptInfo(script) + " / " + restResponse);
    }

    @Override public void scriptCompleted(ApiScript script) { write("scriptCompleted / " + scriptInfo(script)); }

    @Override public void scriptTimedOut(ApiScript script) {
        write("scriptTimedOut / " + scriptInfo(script));
    }

    @Override public void unexpectedResponse(ApiScript script, RestResponse restResponse) {
        write("unexpectedResponse / " + scriptInfo(script) + " / " + restResponse);
    }

    @Override public boolean skipCheck(ApiScript script, ApiScriptResponseCheck check) {
        return false;
    }
}
