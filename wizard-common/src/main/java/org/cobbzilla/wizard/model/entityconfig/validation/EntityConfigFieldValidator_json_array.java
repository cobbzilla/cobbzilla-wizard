package org.cobbzilla.wizard.model.entityconfig.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

public class EntityConfigFieldValidator_json_array extends EntityConfigFieldValidator_json {

    @Override public Class<? extends JsonNode> getJsonClass() { return ArrayNode.class; }

}
