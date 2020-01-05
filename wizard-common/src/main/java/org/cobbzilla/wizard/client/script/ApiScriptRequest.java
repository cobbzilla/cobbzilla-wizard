package org.cobbzilla.wizard.client.script;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.http.HttpMethods;

import java.util.Map;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.json.JsonUtil.mergeJsonOrDie;
import static org.cobbzilla.util.string.StringUtil.ellipsis;

@Accessors(chain=true)
public class ApiScriptRequest {

    @Getter @Setter private String uri;
    @Getter @Setter private String data;
    public boolean hasData () { return !empty(data); }

    @Getter @Setter private boolean handlebarsEnabled = true;

    @Setter private JsonNode entity;
    public JsonNode getEntity () {
        try {
            return entity != null ? entity : entityJson == null ? null : json(entityJson, JsonNode.class);
        } catch (Exception e) {
            if (entityJson != null) return TextNode.valueOf(entityJson);
            return die("getEntity: "+e);
        }
    }
    public boolean hasEntity () { return !empty(entity) || !empty(entityJson); }

    @Getter @Setter private String entityJson;

    @Getter @Setter private String session;
    public boolean hasSession () { return !empty(session); }

    @Getter @Setter private JsonNode headers;
    public boolean hasHeaders () { return !empty(headers); }

    boolean hasHeader(String headerName) {
        return hasHeaders() && getHeaders().has(headerName);
    }

    String getHeader(String headerName) {
        if (hasHeaders() && hasHeader(headerName)) {
            return getHeaders().get(headerName).textValue();
        }
        return null;
    }

    @Setter private String method;

    public String getMethod() {
        if (!empty(method)) return method;
        if (hasEntity() || hasData()) return HttpMethods.POST;
        return HttpMethods.GET;
    }

    public String getJsonEntity(Map<String, Object> ctx) {
        if (data == null) {
            if (entityJson != null) return entityJson;
            return json(entity);
        }
        final Object o = ctx.get(data);
        if (o == null) die("getJsonEntity: data '" + data + "' not found in context: " + ctx);
        return mergeJsonOrDie(json(o), entity);
    }

    @Override public String toString () {
        return "{uri: "+uri
                + ", method: "+getMethod()
                + (hasData() ? ", data: "+ ellipsis(data, 1000) : "")
                + (hasEntity() ? ", entity: " + ellipsis(json(entity), 1000) : "")
                + (hasSession() ? ", session: " + getSession() : "")
                + (hasHeaders() ? ", headers: " + getHeaders() : "")
                +"}";
    }

}
