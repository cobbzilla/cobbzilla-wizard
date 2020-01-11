package org.cobbzilla.wizard.client.script;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.jknack.handlebars.Handlebars;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.handlebars.HandlebarsUtil;
import org.cobbzilla.util.javascript.JsEngine;

import java.util.*;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.json.JsonUtil.json;

@Slf4j
public class ApiInnerScript {

    @Getter @Setter private ApiScript parent;
    @Getter @Setter private ApiInnerScriptRunMode runMode = ApiInnerScriptRunMode.fail_fast;

    @Getter @Setter private Map<String, String> params;
    public boolean hasParams () { return !empty(params); }
    public void setParam(String name, String value) {
        if (params == null) params = new HashMap<>();
        params.put(name, value);
    }

    @Getter @Setter private char paramStartDelim = '[';
    @Getter @Setter private char paramEndDelim = ']';

    @Getter @Setter private Map<String, String> iterate;
    public boolean hasIterate () { return !empty(iterate); }
    public void setIterate(String name, String value) {
        if (iterate == null) iterate = new HashMap<>();
        iterate.put(name, value);
    }

    @Getter @Setter private ApiScript[] scripts;
    public boolean hasScripts () { return !empty(scripts); }

    public List<ApiScript> getAllScripts (JsEngine js, Handlebars handlebars, Map<String, Object> ctx) {
        if (!hasScripts()) {
            log.warn("getAllScripts: no scripts!");
            return Collections.emptyList();
        }
        final List<ApiScript> all = new ArrayList<>();
        if (hasParams()) {
            for (Map.Entry<String, String> param : getParams().entrySet()) {
                ctx.put(param.getKey(), js.evaluate(param.getValue(), ctx));
            }
        }
        if (hasIterate()) {
            final Map<String, String> iterations = new HashMap<>(getIterate());
            applyIterations(js, handlebars, ctx, iterations, all);
        } else {
            all.addAll(buildScripts(handlebars, ctx));
        }
        return all;
    }

    private void applyIterations(JsEngine js, Handlebars handlebars, Map<String, Object> ctx, Map<String, String> iterations, List<ApiScript> scripts) {
        if (empty(iterations)) {
            scripts.addAll(buildScripts(handlebars, ctx));
            return;
        }
        final Map<String, Object> ctxCopy = new HashMap<>(ctx);

        final Iterator<Map.Entry<String, String>> iter = iterations.entrySet().iterator();
        final Map.Entry<String, String> entry = iter.next();
        iter.remove();

        final Object var = js.evaluate(entry.getValue(), ctxCopy);
        if (empty(var)) {
            log.warn("applyIterations: var "+entry.getValue()+" evaluated to something empty: "+var);
        } else {
            final List<Object> resolved = new ArrayList<>();
            if (var.getClass().isArray()) {
                resolved.addAll(Arrays.asList((Object[]) var));
            } else if (var instanceof Collection) {
                resolved.addAll((Collection<?>) var);
            } else {
                resolved.add(var);
            }
            for (Object value : resolved) {
                ctxCopy.put(entry.getKey(), value);
                applyIterations(js, handlebars, ctxCopy, iterations, scripts);
            }
        }
    }

    private List<ApiScript> buildScripts(Handlebars handlebars, Map<String, Object> ctx) {
        final List<ApiScript> scripts = new ArrayList<>();
        for (ApiScript script : getCopyOfScripts()) {
            HandlebarsUtil.applyReflectively(handlebars, script, ctx, paramStartDelim, paramEndDelim);
            scripts.add(script);
        }
        return scripts;
    }

    @JsonIgnore private ApiScript[] getCopyOfScripts() { return json(json(getScripts()), ApiScript[].class); }

}
