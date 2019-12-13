package org.cobbzilla.wizard.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.cobbzilla.util.collection.ArrayUtil;
import org.cobbzilla.wizard.validation.HasValue;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;
import javax.persistence.Transient;
import javax.validation.constraints.Size;
import java.util.Comparator;

import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;

@MappedSuperclass @NoArgsConstructor @Accessors(chain=true) @ToString(of={"name"})
public class NamedIdentityBase implements NamedEntity, Identifiable {

    public static final Comparator<? extends NamedEntity> SORT_NAME_ASC = (Comparator<NamedEntity>) (o1, o2) -> o1.getName().compareTo(o2.getName());

    public static Comparator<? super NamedEntity> sortNameAsc() { return (Comparator<? super NamedEntity>) SORT_NAME_ASC; }

    public static final Comparator<? extends NamedEntity> SORT_NAME_DESC = (Comparator<NamedEntity>) (o1, o2) -> o2.getName().compareTo(o1.getName());
    public static Comparator<? super NamedEntity> sortNameDesc() { return (Comparator<? super NamedEntity>) SORT_NAME_DESC; }

    public NamedIdentityBase (String name) { setName(name); }

    public NamedIdentityBase update(NamedIdentityBase other) { return setName(other.getName()); }

    public static final String[] NI_IGNORABLE_UPDATE_FIELDS = ArrayUtil.append(IGNORABLE_UPDATE_FIELDS, "name");
    @Override public String[] excludeUpdateFields(boolean strict) { return NI_IGNORABLE_UPDATE_FIELDS; }

    @Override public void beforeCreate() {}
    @Override public void beforeUpdate() { setMtime(); }
    @Override public Identifiable update(Identifiable thing) { copy(this, thing, null, NAME_FIELD_ARRAY); return this; }

    @HasValue(message="err.name.empty")
    @Id @Column(length=NAME_MAXLEN, unique=true, nullable=false, updatable=false)
    @Size(min=2, max=NAME_MAXLEN, message="err.name.length")
    @Getter @Setter protected String name;

    @Override @Transient public String getUuid() { return getName(); }
    @Override public void setUuid(String uuid) { setName(uuid); }

    @Column(updatable=false, nullable=false)
    @Getter @JsonIgnore private long ctime = now();
    public void setCtime (long time) { /*noop*/ }
    @JsonIgnore @Transient public long getCtimeAge () { return now() - ctime; }

    @Column(nullable=false)
    @Getter @JsonIgnore private long mtime = now();
    public void setMtime (long time) { this.mtime = time; }
    public void setMtime () { this.mtime = now(); }
    @JsonIgnore @Transient public long getMtimeAge () { return now() - mtime; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NamedIdentityBase)) return false;
        final NamedIdentityBase that = (NamedIdentityBase) o;
        return getName().equals(that.getName());
    }

    @Override public int hashCode() { return getName().hashCode(); }

}
