package org.cobbzilla.wizard.model;

import org.cobbzilla.util.reflect.ReflectionUtil;
import org.cobbzilla.util.string.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

import static org.cobbzilla.util.string.StringUtil.snakeCaseToCamelCase;
import static org.cobbzilla.util.string.StringUtil.sqlEscapeAndQuote;

public interface EntityMask {

    Logger log = LoggerFactory.getLogger(EntityMask.class);

    String[] columns();
    default String valueFor(Identifiable thing, String column) {
        try {
            final Object val = ReflectionUtil.get(thing, snakeCaseToCamelCase(column));
            return val == null ? "NULL" : shouldQuote(column, val)
                    ? sqlEscapeAndQuote(val.toString())
                    : val.toString();
        } catch (Exception e) {
            log.error("valueFor("+thing.getUuid()+", "+column+") returning NULL due to exception: "+e);
            return "NULL";
        }
    }

    default boolean shouldQuote(String column, Object val) { return val instanceof String || val instanceof Enum; }

    default String columnSql () { return StringUtil.toString(Arrays.asList(columns())); }

    default String valueSql (Identifiable thing) {
        final StringBuilder b = new StringBuilder();
        for (String col : columns()) {
            if (b.length() > 0) b.append(",");
            b.append(valueFor(thing, col));
        }
        return b.toString();
    }

}
