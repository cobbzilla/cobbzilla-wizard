package org.cobbzilla.wizard.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.system.Bytes;
import org.cobbzilla.wizard.api.CrudOperation;
import org.cobbzilla.wizard.model.entityconfig.EntityFieldControl;
import org.cobbzilla.wizard.model.entityconfig.EntityFieldMode;
import org.cobbzilla.wizard.model.entityconfig.EntityFieldType;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECField;
import org.cobbzilla.wizard.validation.HasValue;

import javax.persistence.*;
import javax.validation.constraints.Size;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.security.CryptoUtil.string_decrypt;
import static org.cobbzilla.util.security.CryptoUtil.string_encrypt;
import static org.cobbzilla.util.security.ShaUtil.sha256_hex;

@MappedSuperclass @NoArgsConstructor @Accessors(chain=true)
public class AuditLog extends StrongIdentifiableBase {

    private static final int ENC_PAD = 1000;
    public static final int STATE_MAXLEN = (int) (8 * Bytes.MB);

    @HasValue(message="err.entityType.empty")
    @Column(length=200+ENC_PAD, nullable=false, updatable=false)
    @ECField(mode=EntityFieldMode.readOnly)
    @Getter @Setter private String entityType;

    @HasValue(message="err.uuid.empty")
    @Column(length=UUID_MAXLEN+ENC_PAD, nullable=false, updatable=false)
    @ECField(mode=EntityFieldMode.readOnly)
    @Getter @Setter private String entityUuid;

    @Column(length=10, nullable=false, updatable=false)
    @Enumerated(EnumType.STRING)
    @ECField(mode=EntityFieldMode.readOnly)
    @Getter @Setter private CrudOperation operation;

    @Size(max=STATE_MAXLEN, message="err.prevState.length")
    @Column(length=STATE_MAXLEN+ENC_PAD, updatable=false)
    @ECField(mode=EntityFieldMode.readOnly)
    @Getter @Setter private String prevState;
    public boolean hasPrevState() { return !empty(prevState); }

    @Size(max=STATE_MAXLEN, message="err.newState.length")
    @Column(length=STATE_MAXLEN+ENC_PAD, updatable=false)
    @ECField(mode=EntityFieldMode.readOnly)
    @Getter @Setter private String newState;
    public boolean hasNewState() { return !empty(newState); }

    @Size(max=100, message="err.keyHash.length")
    @Column(length=100, nullable=false, updatable=false)
    @ECField(mode=EntityFieldMode.readOnly)
    @Getter @Setter private String keyHash;

    @Column(nullable=false)
    @ECField(mode=EntityFieldMode.readOnly, type= EntityFieldType.flag)
    @Getter @Setter private boolean success = false;

    @Size(max=100, message="err.recordHash.length")
    @Column(length=100, nullable=false, updatable=false)
    @ECField(mode=EntityFieldMode.readOnly)
    @Getter @Setter private String recordHash;

    @ECField(mode=EntityFieldMode.readOnly, control= EntityFieldControl.date, type=EntityFieldType.epoch_time,
            displayName="Record Creation Time")
    @Transient public long getCreateTime () { return getCtime(); }
    public void setCreateTime () {} // noop

    public <E extends AuditLog> E encrypt(String key) {
        setKeyHash(sha256_hex(key));
        setRecordHash(sha256_hex(toString()));
        if (hasPrevState()) setPrevState(string_encrypt(getPrevState(), key));
        if (hasNewState()) setNewState(string_encrypt(getNewState(), key));
        setEntityType(string_encrypt(getEntityType(), key));
        setEntityUuid(string_encrypt(getEntityUuid(), key));
        return (E) this;
    }

    public <E extends AuditLog> E decrypt(String key) {
        if (!sha256_hex(key).equals(getKeyHash())) die("decrypt: wrong key");
        if (hasPrevState()) setPrevState(string_decrypt(getPrevState(), key));
        if (hasNewState()) setNewState(string_decrypt(getNewState(), key));
        setEntityType(string_decrypt(getEntityType(), key));
        setEntityUuid(string_decrypt(getEntityUuid(), key));
        if (!sha256_hex(toString()).equals(recordHash)) die("decrypt: record hash failure");
        return (E) this;
    }

    public String toString() {
        return getUuid()+":"+getCtime()+":"+operation.name()+":"+entityType+ ":" +entityUuid+":"+prevState+":"+newState+":"+keyHash;
    }
}
