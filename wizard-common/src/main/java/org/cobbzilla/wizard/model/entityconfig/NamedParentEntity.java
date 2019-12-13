package org.cobbzilla.wizard.model.entityconfig;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.cobbzilla.util.collection.ArrayUtil;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.NamedIdentityBase;

import javax.persistence.Transient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.json.JsonUtil.json;

@NoArgsConstructor
public abstract class NamedParentEntity extends NamedIdentityBase implements ParentEntity {

    public NamedParentEntity(String name) { super(name); }

    @Transient @Getter @Setter private Map<String, JsonNode[]> children;
    public void initChildren () { children = new HashMap<>(); }

    private static Map<String, Class<? extends Identifiable>> childClasses = new ConcurrentHashMap<>();

    public <T extends Identifiable> List<T> getChildren (Class<T> clazz) { return getChildren(clazz.getSimpleName(), clazz); }

    public <T extends Identifiable> List<T> getChildren (String childType) {
        return getChildren(childType, null);
    }

    public <T extends Identifiable> List<T> getChildren (String childType, Class<T> clazz) {
        if (!hasChildren() || empty(childType)) return new ArrayList<>();
        final JsonNode[] nodes = getChildren().get(childType);
        if (clazz == null) clazz = (Class<T>) childClasses.get(childType);
        return empty(nodes) || clazz == null ? new ArrayList<>() : json(nodes, clazz);
    }

    public boolean hasChildren(Class<? extends Identifiable> clazz) { return !empty(getChildren(clazz)); }

    public <T extends Identifiable> void addChild(Class<T> clazz, T thing) {
        final String simpleName = clazz.getSimpleName();
        cacheChildClass(clazz, simpleName);
        addChild(simpleName, thing);
    }

    protected <T extends Identifiable> void cacheChildClass(Class<T> clazz, String simpleName) {
        final Class<? extends Identifiable> cached = childClasses.get(simpleName);
        if (cached == null) {
            childClasses.put(simpleName, clazz);
        } else if (!clazz.equals(cached)) {
            die("cacheChildClass: two concrete classes with same simple name: "+clazz.getName()+" and "+cached.getName());
        }
    }

    public <T extends Identifiable> void addChildren(Class<T> clazz, T[] things) {
        final String simpleName = clazz.getSimpleName();
        cacheChildClass(clazz, simpleName);
        addChildren(simpleName, things);
    }
    public <T extends Identifiable> void addChildList(Class<T> clazz, List<T> things) {
        if (empty(things)) return;
        final Identifiable[] array = things.toArray(new Identifiable[things.size()]);
        final String simpleName = clazz.getSimpleName();
        cacheChildClass(clazz, simpleName);
        addChildren(simpleName, array);
    }

    private <T extends Identifiable> void addChild(String name, T thing) {
        if (children == null) initChildren();
        final JsonNode[] existing = children.get(name);
        if (existing == null) {
            children.put(name, json(json(new Identifiable[] {thing}), JsonNode[].class));
        } else {
            children.put(name, ArrayUtil.append(existing, json(json(thing), JsonNode.class)));
        }
    }

    private <T extends Identifiable> void addChildren(String name, T[] things) {
        if (children == null) initChildren();
        final JsonNode[] existing = children.get(name);
        if (existing == null) {
            children.put(name, json(json(things), JsonNode[].class));
        } else {
            children.put(name, ArrayUtil.concat(existing, json(json(things), JsonNode[].class)));
        }
    }
}
