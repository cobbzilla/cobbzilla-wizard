package org.cobbzilla.wizard.model.context;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import static org.cobbzilla.util.json.JsonUtil.json;

@NoArgsConstructor @Accessors(chain=true)
public class ContextEntryNode extends ContextEntry {

    public JsonNode getNode () { return json(getJson(), JsonNode.class); }
    public void setNode (JsonNode node) { setJson(json(node)); }

}
