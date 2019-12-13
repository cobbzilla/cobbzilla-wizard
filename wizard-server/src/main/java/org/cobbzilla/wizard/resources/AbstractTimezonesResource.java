package org.cobbzilla.wizard.resources;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.NameAndValue;
import org.cobbzilla.util.time.UnicodeTimezone;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.util.*;
import java.util.function.Function;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toCollection;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.cobbzilla.util.collection.NameAndValue.NAME_COMPARATOR;
import static org.cobbzilla.util.time.UnicodeTimezone.getUnicodeTimezoneMap;
import static org.cobbzilla.wizard.resources.ResourceUtil.notFound;
import static org.cobbzilla.wizard.resources.ResourceUtil.ok;

@Slf4j
@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
public class AbstractTimezonesResource {

    public static final String FORWARD_SLASH = "/";

    public static final String VERTICAL_LOW_LINE = "︳";
    public static final String FULL_LOW_LINE = "＿";
    public static final String DASHED_LOW_LINE = "﹍";

    public static final String[] TZ_FORMAT_CHARS = { VERTICAL_LOW_LINE, FULL_LOW_LINE, DASHED_LOW_LINE };

    @AllArgsConstructor
    public enum TzFormat {
        raw (identity()),
        full (identity()),
        desc (identity()),
        vll (v -> v.replace(FORWARD_SLASH, VERTICAL_LOW_LINE)),
        fll (v -> v.replace(FORWARD_SLASH, FULL_LOW_LINE)),
        dll (v -> v.replace(FORWARD_SLASH, DASHED_LOW_LINE));

        private Function<String, String> formatFunction;

        @JsonCreator public static TzFormat fromString(String v) { return valueOf(v.toLowerCase()); }

        public String format(String val) { return formatFunction.apply(val); }
    }

    private static final Set<String> all_raw
            = getUnicodeTimezoneMap().keySet().stream().map(TzFormat.raw::format).collect(toCollection(TreeSet::new));

    private static final Map<String, UnicodeTimezone> all_full = getUnicodeTimezoneMap();

    private static final Set<NameAndValue> all_desc = initAllWithDescriptions();
    private static Set<NameAndValue> initAllWithDescriptions() {
        final Set<NameAndValue> vals = new TreeSet<>(NAME_COMPARATOR);
        final Map<String, UnicodeTimezone> utzMap = getUnicodeTimezoneMap();
        utzMap.forEach((k, v) -> vals.add(new NameAndValue(k, v.getDescription())));
        return vals;
    }

    private static final Set<String> all_vll
            = getUnicodeTimezoneMap().keySet().stream().map(TzFormat.vll::format).collect(toCollection(TreeSet::new));

    private static final Set<String> all_fll
            = getUnicodeTimezoneMap().keySet().stream().map(TzFormat.fll::format).collect(toCollection(TreeSet::new));

    private static final Set<String> all_dll
            = getUnicodeTimezoneMap().keySet().stream().map(TzFormat.dll::format).collect(toCollection(TreeSet::new));

    @GET
    public Response findAll (@PathParam("id") String id,
                             @QueryParam("format") TzFormat format) {
        final TzFormat fmt = format != null ? format : TzFormat.raw;
        switch (fmt) {
            case full: return ok(all_full);
            case desc: return ok(all_desc);
            case vll: return ok(all_vll);
            case fll: return ok(all_fll);
            case dll: return ok(all_dll);
            default: case raw: return ok(all_raw);
        }
    }

    @GET @Path("/{id: .*}")
    public Response find (@PathParam("id") String id) {

        UnicodeTimezone utz = UnicodeTimezone.fromString(id);
        if (utz != null) return ok(utz.getName());

        for (String c : TZ_FORMAT_CHARS) {
            utz = UnicodeTimezone.fromString(id.replace(c, FORWARD_SLASH));
            if (utz != null) return ok(utz.getName());
        }

        return notFound(id);
    }

}
