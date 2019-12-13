package org.cobbzilla.wizard.model.entityconfig;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

/**
 * Interface that Entity classes are required to implement if they are parents.
 */
public interface ParentEntity {

    Map<String, JsonNode[]> getChildren();
    void setChildren(Map<String, JsonNode[]> children);

    default boolean hasChildren() { return !empty(getChildren()); }

}
