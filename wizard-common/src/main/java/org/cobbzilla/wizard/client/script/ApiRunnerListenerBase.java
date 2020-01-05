package org.cobbzilla.wizard.client.script;

import lombok.Getter;
import lombok.Setter;
import org.cobbzilla.util.string.StringUtil;
import org.cobbzilla.wizard.util.RestResponse;

import java.util.Map;

import static java.lang.System.identityHashCode;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;

public class ApiRunnerListenerBase implements ApiRunnerListener {

    public ApiRunnerListenerBase (String name) { setName(name); }

    @Getter @Setter private String name;

    @Getter private ApiRunner apiRunner;
    @Override public void setApiRunner(ApiRunner runner) {
        if (apiRunner != null) {
            die("setApiRunner: runner already set: "+apiRunner);
        }
        apiRunner = runner;
    }

    public String getId () { return getName() + "/" + identityHashCode(this); }

    @Override public void beforeCall(ApiScript script, Map<String, Object> ctx) {}
    @Override public void afterCall(ApiScript script, Map<String, Object> ctx, RestResponse response) {}

    @Override public void statusCheckFailed(ApiScript script, String uri, RestResponse restResponse) {
        if (script.isTimedOut()) {
            die("statusCheckFailed(" + getId() + "): request " + script.getRequestLine() + " (resulting with " + uri
                + ") expected " + script.getResponse().getStatus() + ", returned response " + restResponse.toString());
        }
    }

    @Override public void conditionCheckFailed(String message, ApiScript script, RestResponse restResponse,
                                               ApiScriptResponseCheck check, Map<String, Object> ctx) {
        if (script.isTimedOut()) {
            die("conditionCheckFailed(" + getId() + "): " + script.getRequestLine() + ":\n" +
                    message + "\n" +
                    "failed condition=" + check + "\n" +
                    "server response=" + restResponse + "\n" +
                    "ctx=" + StringUtil.toString(ctx));
        }
    }

    @Override public void sessionIdNotFound(ApiScript script, RestResponse restResponse) {
        if (script.isTimedOut()) die("sessionIdNotFound("+getId()+"): expected "+script.getResponse().getSession()+", server response="+restResponse);
    }

    @Override public void scriptCompleted(ApiScript script) {}

    @Override public void scriptTimedOut(ApiScript script) { die("scriptTimedOut: script="+script+", timed out"); }

    @Override public void unexpectedResponse(ApiScript script, RestResponse restResponse) {
        if (script.isTimedOut()) die("unexpectedResponse("+getId()+"): script="+script+", server response="+restResponse);
    }

    @Override public void beforeScript(String before, Map<String, Object> ctx) throws Exception {}
    @Override public void afterScript(String after, Map<String, Object> ctx) throws Exception {}

    @Override public boolean skipCheck(ApiScript script, ApiScriptResponseCheck check) { return false; }

}
