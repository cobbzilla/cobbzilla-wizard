package org.cobbzilla.wizard.model.anon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.json.JsonUtil;
import org.cobbzilla.util.reflect.ReflectionUtil;
import org.jasypt.hibernate4.encryptor.HibernatePBEStringEncryptor;

import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.regex.Pattern;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.json.JsonUtil.*;

@Accessors(chain=true) @ToString(of="name") @Slf4j
public class AnonColumn {

    public static final String[] MERGE_FIELDS = {"encrypted", "value", "json", "skip", "type"};

    @Getter @Setter private String name;
    @Getter @Setter private boolean encrypted = false;
    @Getter @Setter private String value;
    @Getter @Setter private AnonJsonPath[] json;

    @Setter private String[] skip;
    public String[] getSkip() { return empty(skip) ? null : skip; }

    @Setter private AnonType type;
    public AnonType getType() {
        return type != null
            ? type
            : !empty(json) || !empty(value)
                ? AnonType.passthru
                : AnonType.guessType(getName()); }

    public void setParam(PreparedStatement ps,
                         HibernatePBEStringEncryptor decryptor,
                         HibernatePBEStringEncryptor encryptor,
                         int index, Object val) throws Exception {
        String value = (val == null) ? null : val.toString();
        if (value == null) {
            ps.setNull(index, Types.VARCHAR);
            return;
        }

        if (encrypted) {
            try {
                value = decryptor.decrypt(value);
            } catch (Exception e) {
                // Has it already been anonymized and encrypted?
                try {
                    value = encryptor.decrypt(value);
                } catch (Exception e2) {
                    if (value.endsWith("==")) {
                        die("setParam: error decrypting " + name + ": " + value);
                    } else {
                        log.warn("setParam: error decrypting " + name + " (handling as plaintext): " + value);
                    }
                }
            }
        }

        if (!shouldSkip(value)) {
            if (this.value != null) {
                value = this.value;
            } else {
                if (json == null) {
                    value = getType().transform(value);
                } else {
                    value = transformJson(value);
                }
            }
        }

        if (encrypted && value != null) value = encryptor.encrypt(value);

        if (value == null) {
            ps.setNull(index, Types.VARCHAR);
        } else if (val instanceof Long){
            ps.setLong(index, Long.parseLong(value));
        } else if (val instanceof Integer){
            ps.setInt(index, Integer.parseInt(value));
        } else {
            ps.setString(index, value);
        }
    }

    private String transformJson(String value) throws Exception {
        ObjectNode node = JsonUtil.json(value, ObjectNode.class);
        for (AnonJsonPath jsonPath : json) {
            final JsonNode toReplace = findNode(node, jsonPath.getPath());
            if (toReplace != null) {
                final String val = toReplace.textValue();
                node = replaceNode(node, jsonPath.getPath(), jsonPath.getType().transform(val));
            }
        }
        return toJson(node);
    }

    @Getter(lazy=true) private final Pattern[] skipPatterns = initSkipPatterns();
    private Pattern[] initSkipPatterns() {
        if (empty(skip)) return null;
        final Pattern[] patterns = new Pattern[skip.length];
        if (skip != null) for (int i=0; i<skip.length; i++) patterns[i] = Pattern.compile(skip[i]);
        return patterns;
    }

    private boolean shouldSkip(String value) {
        if (skip == null || skip.length == 0) return false;
        for (Pattern p : getSkipPatterns()) {
            if (p.matcher(value).find()) return true;
        }
        return false;
    }

    public void merge(AnonColumn other) { ReflectionUtil.copy(this, other, MERGE_FIELDS); }
}
