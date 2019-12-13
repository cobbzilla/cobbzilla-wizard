package org.cobbzilla.wizard.model.context;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.Type;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.Transient;
import javax.validation.constraints.Size;
import java.util.*;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.reflect.ReflectionUtil.forName;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENCRYPTED_STRING;

@Embeddable @NoArgsConstructor @Accessors(chain=true) @Slf4j
public class SavedContext {

    public static final int CONTEXT_JSON_MAXLEN = 10_000_000;

    @Size(max=CONTEXT_JSON_MAXLEN, message="err.contextJson.length")
    @Column(columnDefinition="TEXT NOT NULL")
    @JsonIgnore @Type(type=ENCRYPTED_STRING) @Getter @Setter private String contextJson = "[]";

    public SavedContext(Map<String, Object> context) { setContext(context); }

    public boolean containsKey (String key) { return getContext().containsKey(key); }

    public void put (String key, Object object) {
        final Map<String, Object> ctx = new HashMap<>(getContext());
        ctx.put(key, object);
        setContext(ctx);
    }

    public void putAll (Map<String, Object> values) {
        if (empty(values)) return;
        final Map<String, Object> ctx = new HashMap<>(getContext());
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (entry.getValue() != null) ctx.put(entry.getKey(), entry.getValue());
        }
        setContext(ctx);
    }

    @Transient public Map<String, Object> getContext () {
        final ContextEntry[] entries = json(contextJson, ContextEntry[].class);
        final Map<String, Object> map = new LinkedHashMap<>();
        for (ContextEntry entry : entries) {
            map.put(entry.getName(), json(entry.getJson(), forName(entry.getClassName())));
        }
        return map;
    }
    public void setContext(Map<String, Object> map) {
        final List<ContextEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            final String key = entry.getKey();
            final Object thing = entry.getValue();
            final String className;
            if (thing != null) {
                if (thing instanceof Collection) {
                    final Collection c = (Collection) thing;
                    className = c.isEmpty() ? c.getClass().getName() : c.iterator().next().getClass().getName() + "[]";
                } else {
                    className = thing.getClass().getName();
                }
                entries.add(new ContextEntry(key, className, json(thing)));
            } else {
                log.debug("setContext: skipping key '"+ key +"' because value was null");
            }
        }
        contextJson = json(entries);
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SavedContext that = (SavedContext) o;
        return getContextJson().equals(that.getContextJson());
    }

    @Override public int hashCode() { return getContextJson().hashCode(); }

    public boolean isSame(Map<String, Object> ctx) {
        final Map<String, Object> thisCtx = getContext();
        if (empty(thisCtx)) return empty(ctx);
        if (thisCtx.size() != ctx.size()) return false;
        for (Map.Entry<String, Object> entry : thisCtx.entrySet()) {
            final Object val = ctx.get(entry.getKey());
            if (entry.getValue() == null && val != null) return false;
            if (!entry.getValue().equals(val)) return false;
        }
        return true;
    }

}
