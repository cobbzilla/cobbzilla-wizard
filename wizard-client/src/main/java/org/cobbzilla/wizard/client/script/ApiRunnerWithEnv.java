package org.cobbzilla.wizard.client.script;

import org.cobbzilla.util.javascript.StandardJsEngine;
import org.cobbzilla.wizard.client.ApiClientBase;

import java.util.Map;

public class ApiRunnerWithEnv extends ApiRunner {

    private Map<String, String> env;

    public ApiRunnerWithEnv(ApiClientBase api,
                            StandardJsEngine js,
                            ApiRunnerListener listener,
                            ApiScriptIncludeHandler includeHandler,
                            Map<String, String> env) {
        super(js, api, listener, includeHandler);
        this.env = env;
    }

    @Override public Map<String, Object> getContext() {
        final Map<String, Object> ctx = super.getContext();
        ctx.putAll(env);
        return ctx;
    }
}
