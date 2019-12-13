package org.cobbzilla.wizard.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.cobbzilla.util.collection.ArrayUtil;
import org.hibernate.annotations.Type;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;
import javax.validation.constraints.Size;

import static org.cobbzilla.util.reflect.ReflectionUtil.copy;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENCRYPTED_STRING;
import static org.cobbzilla.wizard.model.crypto.EncryptedTypes.ENC_PAD;

@MappedSuperclass @NoArgsConstructor @AllArgsConstructor
public class AssetStorageInfoBase extends IdentifiableBase implements AssetStorageInfo, NamedEntity {

    public static final int ASSET_MAXLEN = 1024;

    protected static final String[] UPDATE_FIELDS = {"asset"};
    protected static final String[] CREATE_FIELDS = ArrayUtil.append(UPDATE_FIELDS, "relatedEntity", "name");

    public AssetStorageInfoBase(AssetStorageInfoBase other) { copy(this, other, CREATE_FIELDS); }

    @Override public Identifiable update(Identifiable other) { copy(this, other, UPDATE_FIELDS); return this; }

    @Size(max=NAME_MAXLEN, message="err.name.length")
    @Column(length=NAME_MAXLEN, nullable=false)
    @Getter @Setter private String name;

    @Column(columnDefinition="varchar("+(ASSET_MAXLEN+ENC_PAD)+")")
    @Type(type=ENCRYPTED_STRING) @Getter @Setter private String asset;

    @Column(length=UUID_MAXLEN, updatable=false, nullable=false)
    @Getter @Setter private String relatedEntity;

}