package org.cobbzilla.wizard.model.anon;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;

import java.util.concurrent.TimeUnit;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.security.ShaUtil.sha256_hex;
import static org.cobbzilla.wizard.util.TestNames.*;

@AllArgsConstructor @Slf4j
public enum AnonType {

    passthru(value -> value),

    name(value -> safeColor(value) + " " + safeNationality(value)),

    description(value -> "lorem ipsum the " + safeColor(value).toLowerCase() + " " + safeAnimal(value).toLowerCase() + " jumped over the lazy dog"),

    address(value -> (Long.parseLong(sha256_hex(value).substring(0, 4), 16) % 10000) +  " " + safeFruit(value) + " Street"),

    email(value -> value.endsWith("@example.com") ? value : safeAnimal(value)+"@example.com"),

    url(value -> value.contains("example.com") ? value : "http://example.com/"+safeAnimal(value)+"/anonymized"),

    phone(value -> "8005551212"),

    license(value -> "111222333"),

    text50(value -> randomAlphanumeric(50)),

    initials(value ->
            "" + ((char) ('A'+Integer.parseInt(sha256_hex(value).substring(0, 4), 16) % 26))
               + ((char) ('A'+Integer.parseInt(sha256_hex(value).substring(4, 8), 16) % 26))),

    // dummy signature
    signature(value -> "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"),

    fein(value -> "123456789"),

    expiration((SqlTransform) value -> empty(value) ? value : String.valueOf(Long.parseLong(value) + RandomUtils.nextLong(TimeUnit.DAYS.toMillis(10), TimeUnit.DAYS.toMillis(1000))));

    @Getter private final SqlTransform transformer;

    @JsonCreator public static AnonType fromString (String val) { return valueOf(val.toLowerCase()); }

    public String transform (String value) { return getTransformer().transform(value); }

    public interface SqlTransform { String transform (String value); }

    public static AnonType guessType(String name) {
        if (empty(name)) {
            return AnonType.passthru;
        }
        final String c = name.toLowerCase();
        for (AnonType t : values()) if (c.contains(t.name())) return t;
        return die("guessType(" + name + "): no type could be determined");
    }

}
