package org.cobbzilla.wizard.model;

import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;
import javax.validation.constraints.Size;

@MappedSuperclass @NoArgsConstructor
public abstract class UniquelyNamedEntity extends IdentifiableBase implements NamedEntity {

    public UniquelyNamedEntity (String name) { setName(name); }

    public UniquelyNamedEntity(UniquelyNamedEntity other) { setName(other.getName()); }

    public boolean forceLowercase () { return true; }

    @Column(length=NAME_MAXLEN, unique=true, nullable=false, updatable=false)
    @Size(min=2, max=NAME_MAXLEN, message="err.name.length")
    protected String name;
    public String getName () { return hasName() ? (forceLowercase() ? name.toLowerCase() : name) : name; }
    public UniquelyNamedEntity setName (String name) { this.name = (name == null ? null : forceLowercase() ? name.toLowerCase() : name); return this; }

    public boolean isSameName(UniquelyNamedEntity other) { return getName().equals(other.getName()); }

    @Override public String toString() { return "{"+getName() + ": "+super.toString()+"}"; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UniquelyNamedEntity)) return false;
        final UniquelyNamedEntity that = (UniquelyNamedEntity) o;
        return getName().equals(that.getName());
    }

    @Override public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + getName().hashCode();
        return result;
    }
}
