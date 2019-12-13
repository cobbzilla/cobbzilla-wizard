package org.cobbzilla.wizard.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.cobbzilla.util.collection.FieldTransformer;
import org.cobbzilla.util.string.StringUtil;
import org.cobbzilla.wizard.model.entityconfig.EntityFieldMode;
import org.cobbzilla.wizard.model.entityconfig.EntityFieldType;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECField;
import org.hibernate.cfg.ImprovedNamingStrategy;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;
import javax.persistence.Transient;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;

@MappedSuperclass @Slf4j
public class IdentifiableBase implements Identifiable {

    public String simpleName () { return getClass().getSimpleName(); }
    public String propName () { return StringUtil.uncapitalize(getClass().getSimpleName()); }

    public String tableName () { return ImprovedNamingStrategy.INSTANCE.classToTableName(getClass().getName()); }
    public String tableName (String className) { return ImprovedNamingStrategy.INSTANCE.classToTableName(className); }

    public String columnName () { return ImprovedNamingStrategy.INSTANCE.propertyToColumnName(propName()); }
    public String columnName (String propName) { return ImprovedNamingStrategy.INSTANCE.propertyToColumnName(propName); }

    public static final Comparator<IdentifiableBase> CTIME_DESC = (o1, o2) -> Long.compare(o2.getCtime(), o1.getCtime());
    public static final Comparator<IdentifiableBase> CTIME_ASC = (o1, o2) -> Long.compare(o1.getCtime(), o2.getCtime());

    @Id @Column(unique=true, updatable=false, nullable=false, length=UUID_MAXLEN)
    @Getter @Setter private volatile String uuid = null;
    public boolean hasUuid () { return !empty(uuid); }

    public static final int DEFAULT_SHORT_ID_LENGTH = 8;
    @Transient @JsonIgnore public int getShortIdLength () { return DEFAULT_SHORT_ID_LENGTH; }
    @Transient public String getShortId () { return !hasUuid() ? null : getUuid().length() < getShortIdLength() ? getUuid() : getUuid().substring(0, getShortIdLength()); }
    public void setShortId (String id) {} // noop

    @Override public String[] excludeUpdateFields(boolean strict) { return IGNORABLE_UPDATE_FIELDS; }

    @Transient @JsonIgnore
    @Getter private static final ThreadLocal<Boolean> enforceNullUuidOnCreate = new ThreadLocal<>();

    public void beforeCreate() {
        if (uuid != null) {
            if (enforceNullUuidOnCreate.get() == null || enforceNullUuidOnCreate.get()) {
                die("uuid already initialized on " + getClass().getName());
            } else {
                log.debug("beforeCreate: uuid already set but enforceNullUuidOnCreate was false");
                return;
            }
        }
        initUuid();
    }

    @Override public void beforeUpdate() { setMtime(); }

    public void initUuid() { setUuid(java.util.UUID.randomUUID().toString()); }

    @Override public Identifiable update(Identifiable thing) { return update(thing, null); }

    public Identifiable update(Identifiable thing, String[] fields) {
        String existingUuid = getUuid();
        try {
            copy(this, thing, fields);
            return this;

        } catch (Exception e) {
            throw new IllegalArgumentException("update: error copying properties: "+e, e);

        } finally {
            // Do not allow these to be updated
            setUuid(existingUuid);
        }
    }

    @Column(updatable=false, nullable=false)
    @ECField(type=EntityFieldType.epoch_time, mode=EntityFieldMode.readOnly)
    @Getter @Setter @JsonIgnore private long ctime = now();
    @JsonIgnore @Transient public long getCtimeAge () { return now() - ctime; }

    @Column(nullable=false)
    @ECField(type=EntityFieldType.epoch_time)
    @Getter @Setter @JsonIgnore private long mtime = now();
    public void setMtime () { setMtime(now()); }
    @JsonIgnore @Transient public long getMtimeAge () { return now() - mtime; }

    @Override public String toString() { return simpleName()+"{uuid=" + uuid + "}"; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IdentifiableBase)) return false;
        final IdentifiableBase that = (IdentifiableBase) o;
        return getUuid() != null ? getUuid().equals(that.getUuid()) : that.getUuid() == null;
    }

    @Override public int hashCode() { return getUuid() != null ? getUuid().hashCode() : 0; }

    public static String[] toUuidArray(List<? extends Identifiable> entities) {
        return empty(entities)
                ? StringUtil.EMPTY_ARRAY
                : (String[]) collectArray(entities, "uuid");
    }

    public static List<String> toUuidList(Collection<? extends Identifiable> entities) {
        if (empty(entities)) return Collections.emptyList();
        return collectList(entities, "uuid");
    }

    private static final Map<String, FieldTransformer> fieldTransformerCache = new ConcurrentHashMap<>();
    protected static FieldTransformer getFieldTransformer(String field) {
        FieldTransformer f = fieldTransformerCache.get(field);
        if (f == null) {
            f = new FieldTransformer(field);
            fieldTransformerCache.put(field, f);
        }
        return f;
    }

    public static <T> T[] collectArray(Collection<? extends Identifiable> entities, String field) {
        return (T[]) CollectionUtils.collect(entities, getFieldTransformer(field)).toArray(new String[entities.size()]);
    }
    public static <T> List<T> collectList(Collection<? extends Identifiable> entities, String field) {
        return (List<T>) CollectionUtils.collect(entities, getFieldTransformer(field));
    }
    public static List<String> collectStringList(Collection<? extends Identifiable> entities, String field) {
        return (List<String>) CollectionUtils.collect(entities, getFieldTransformer(field));
    }

}
