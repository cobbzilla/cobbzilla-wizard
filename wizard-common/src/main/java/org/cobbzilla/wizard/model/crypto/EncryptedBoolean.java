package org.cobbzilla.wizard.model.crypto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.hibernate.annotations.Type;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.Transient;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENCRYPTED_STRING;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENC_PAD;

@Embeddable @NoArgsConstructor @Accessors(chain=true)
public class EncryptedBoolean {

    public static final int COLUMN_MAXLEN = 20 + ENC_PAD;

    public static EncryptedBoolean trueValue  () { return new EncryptedBoolean(true); }
    public static EncryptedBoolean falseValue () { return new EncryptedBoolean(false); }

    public EncryptedBoolean (Boolean val) { set(val); }

    private EncryptedBoolean set(Boolean val) { return val == null ? setNull() : val ? setTrue() : setFalse(); }

    public static final String TRUE_SUFFIX = "_true";
    public static final String FALSE_SUFFIX = "_false";
    public static final String NULL_SUFFIX = "_null";

    @Type(type=ENCRYPTED_STRING) @Column(columnDefinition="varchar("+ COLUMN_MAXLEN +") NOT NULL")
    @Getter @Setter private String flag = randomAlphanumeric(10) + NULL_SUFFIX;

    public EncryptedBoolean setTrue  () { return setFlag(randomAlphanumeric(10) + TRUE_SUFFIX); }
    public EncryptedBoolean setFalse () { return setFlag(randomAlphanumeric(10) + FALSE_SUFFIX); }
    public EncryptedBoolean setNull  () { return setFlag(randomAlphanumeric(10) + NULL_SUFFIX); }

    @Transient @JsonIgnore public boolean isTrue () { return flag != null && flag.endsWith(TRUE_SUFFIX); }
    @Transient @JsonIgnore public boolean isFalse () { return flag != null && flag.endsWith(FALSE_SUFFIX); }
    @Transient @JsonIgnore public boolean isNull () { return !isTrue() && !isFalse(); }

}
