package org.cobbzilla.wizard.docstore.mongo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.jmkgreen.morphia.annotations.Id;
import com.github.jmkgreen.morphia.annotations.Indexed;
import lombok.Getter;
import lombok.Setter;
import org.bson.types.ObjectId;
import org.cobbzilla.util.collection.ArrayUtil;
import org.cobbzilla.wizard.model.Identifiable;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;
import static org.cobbzilla.wizard.model.BasicConstraintConstants.ERR_UUID_LENGTH;

public class MongoDocBase implements Identifiable {

    private static final String[] UPDATE_EXCLUDES = ArrayUtil.append(UUID_ARRAY, "id");

    @Id @JsonIgnore @Getter @Setter
    private ObjectId id;

    @Getter @Setter @Indexed
    @Size(max=UUID_MAXLEN, message=ERR_UUID_LENGTH)
    private String uuid;

    @Override public String[] excludeUpdateFields(boolean strict) { return UPDATE_EXCLUDES; }

    @Override public void beforeCreate() {
        if (uuid != null) return; // caller is supplying it to link to something else
        uuid = java.util.UUID.randomUUID().toString();
    }

    @Override public void beforeUpdate() {}

    @Override public Identifiable update(Identifiable thing) { copy(this, thing, null, UPDATE_EXCLUDES); return this; }

    @NotNull @Setter private long ctime = now();
    @JsonIgnore public long getCtime () { return ctime; }

}
