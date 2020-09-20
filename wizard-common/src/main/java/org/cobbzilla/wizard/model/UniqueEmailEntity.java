package org.cobbzilla.wizard.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import org.cobbzilla.wizard.validation.HasValue;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;
import javax.persistence.Transient;
import javax.validation.constraints.Email;
import javax.validation.constraints.Size;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

@MappedSuperclass @NoArgsConstructor
@EqualsAndHashCode(of={"email"}, callSuper=false)
@ToString(callSuper=true)
public class UniqueEmailEntity extends IdentifiableBase {

    public static final int EMAIL_MAXLEN = 100;

    public UniqueEmailEntity (String email) { setEmail(email); }

    @Email(message="err.email.invalid")
    @HasValue(message="err.email.empty")
    @Column(length=EMAIL_MAXLEN, unique=true, nullable=false, updatable=false)
    @Size(min=2, max=EMAIL_MAXLEN, message="err.email.length")
    @Getter protected String email;
    public boolean hasEmail () { return !empty(email); }

    public UniqueEmailEntity setEmail (String email) { this.email = (email == null ? null : email.toLowerCase()); return this; }

    public boolean isSameEmail(UniqueEmailEntity other) { return getEmail().equalsIgnoreCase(other.getEmail()); }

    @JsonIgnore @Transient public String getName () { return getEmail(); }

}
