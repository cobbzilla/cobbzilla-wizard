package org.cobbzilla.wizard.model;

import lombok.Cleanup;
import org.cobbzilla.util.string.Base64;
import org.cobbzilla.util.string.StringUtil;

import java.io.*;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;

public interface Identifiable extends Serializable {

    String UUID = "uuid";
    String[] UUID_ARRAY = {UUID};

    int UUID_MAXLEN = BasicConstraintConstants.UUID_MAXLEN;

    String ENTITY_TYPE_HEADER_NAME = "ZZ-TYPE";

    String[] IGNORABLE_UPDATE_FIELDS = { "uuid", "name", "children", "ctime", "mtime" };
    default String[] excludeUpdateFields(boolean strict) { return StringUtil.EMPTY_ARRAY; }

    String getUuid();
    void setUuid(String uuid);

    void beforeCreate();
    void beforeUpdate();

    default Identifiable update(Identifiable thing) {
        copy(this, thing, null, excludeUpdateFields(true));
        return this;
    }

    default String serialize() { return serialize(this); }

    static String serialize(Identifiable entity) {
        try {
            final ByteArrayOutputStream bout = new ByteArrayOutputStream();
            @Cleanup final ObjectOutputStream out = new ObjectOutputStream(bout);
            out.writeObject(entity);
            out.flush();
            return Base64.encodeBytes(bout.toByteArray());
        } catch (Exception e) {
            return die("serialize: " + e);
        }
    }

    static Identifiable deserialize(String data) {
        try {
            final ByteArrayInputStream bis = new ByteArrayInputStream(Base64.decode(data));
            @Cleanup final ObjectInput in = new ObjectInputStream(bis);
            return (Identifiable) in.readObject();
        } catch (Exception e) {
            return die("deserialize: "+e);
        }
    }

}
