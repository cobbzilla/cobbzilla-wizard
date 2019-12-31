package org.cobbzilla.wizard.model.entityconfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.model.entityconfig.validation.*;
import org.cobbzilla.wizard.validation.ValidationResult;
import org.cobbzilla.wizard.validation.Validator;

import javax.persistence.Column;
import java.lang.reflect.Field;
import java.util.Locale;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

@AllArgsConstructor @Slf4j
public enum EntityFieldType {

    /** it holds a place where nothing was set */
    none_set (new EntityConfigFieldValidator() {
        @Override public ValidationResult validate(Locale locale, Validator validator, EntityFieldConfig fieldConfig, Object value) {
            return new ValidationResult("err.ec.fieldType.none_set");
        }
        @Override public Object toObject(Locale locale, String value) { return null; }
    }),

    /** a string of characters */
    string (new EntityConfigFieldValidator_string()),

    /** a string containing an email address */
    email (new EntityConfigFieldValidator_email()),

    /** an integer-valued number */
    integer (new EntityConfigFieldValidator_integer()),

    /** a real number */
    decimal  (new EntityConfigFieldValidator_decimal()),

    /** an integer-valued monetary amount */
    money_integer  (null),

    /** a real-valued monetary amount */
    money_decimal  (null),

    /** a boolean value */
    flag  (new EntityConfigFieldValidator_boolean()),

    /** a date value */
    date  (null),

    /** a date value in the past (before current date) */
    date_past  (null),

    /** a date value in the future (or current date) */
    date_future  (null),

    /** a field for age */
    age  (null),

    /** a 4-digit year field */
    year  (null),

    /** a 4-digit year field that starts with the current year and goes into the past */
    year_past  (null),

    /** a 4-digit year field that starts with the current year and goes into the future */
    year_future  (null),

    /** a 4-digit year and 2-digit month field (YYYY-MM) */
    year_and_month  (null),

    /** a 4-digit year and 2-digit month field (YYYY-MM) field that starts with the current year and goes into the past */
    year_and_month_past  (null),

    /** a 4-digit year and 2-digit month field (YYYY-MM) field that starts with the current year and goes into the future */
    year_and_month_future  (null),

    /** a date or date/time value, represented as milliseconds since 1/1/1970 */
    epoch_time  (new EntityConfigFieldValidator_integer()),

    /** a date or date/time value, represented as milliseconds since 1/1/1970 */
    expiration_time  (new EntityConfigFieldValidator_integer()),

    /** a time-zone (for example America/New York) */
    time_zone  (null),

    /** a locale (for example en_US) */
    locale  (null),

    /** an IPv4 address */
    ip4  (null),

    /** an IPv6 address */
    ip6  (null),

    /** a 2-letter US state abbreviation */
    us_state  (null),

    /** a US ZIP code */
    us_zip  (null),

    /** HTTP URL */
    http_url (new EntityConfigFieldValidator_httpUrl()),

    /** a reference to another EntityConfig instance */
    reference  (null),

    /** a base64-encoded PNG image  */
    base64_png  (null),

    /** an embedded sub-object */
    embedded  (new EntityConfigFieldValidator_embedded()),

    /** a US phone number */
    us_phone (new EntityConfigFieldValidator_USPhone());

    private EntityConfigFieldValidator fieldValidator;

    /** Jackson-hook to create a new instance based on a string, case-insensitively */
    @JsonCreator public static EntityFieldType fromString(String val) { return valueOf(val.toLowerCase()); }

    public static EntityFieldType safeFromString(String val) {
        try {
            return valueOf(val.toLowerCase());
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isNullable(Field f) {
        final Column column = f.getAnnotation(Column.class);
        if (column == null) return !f.getType().isPrimitive();
        if (!column.nullable()) return false;
        if (!empty(column.columnDefinition()) && column.columnDefinition().toUpperCase().contains("NOT NULL")) return false;
        return true;
    }

    public static int safeColumnLength(Field f) {
        final Integer val = columnLength(f);
        return val == null ? -1 : val;
    }

    public static Integer columnLength(Field f) {
        final Column column = f.getAnnotation(Column.class);
        return column == null ? null : column.length();
    }

    public static EntityFieldType guessFieldType(Field f) {
        switch (f.getType().getName()) {
            case "boolean":
            case "java.lang.Boolean":
                return flag;
            case "long":
            case "java.lang.Long":
                if (f.getName().equals("ctime") || f.getName().equals("mtime")) return epoch_time;
            case "byte":
            case "short":
            case "int":
            case "java.lang.Byte":
            case "java.lang.Short":
            case "java.lang.Integer":
            case "java.math.BigInteger":
                return integer;
            case "char":
            case "java.lang.Character":
            case "java.lang.String":
                return string;
            case "float":
            case "double":
            case "java.lang.Float":
            case "java.lang.Double":
            case "java.math.BigDecimal":
                return decimal;
            default:
                if (f.getType().isEnum()) return string;
                log.warn("guessFieldType: unrecognized type ("+f.getType().getName()+") for field: "+f.getName());
                return null;
        }
    }

    public Object toObject(Locale locale, String value) {
        return fieldValidator == null ? value : fieldValidator.toObject(locale, value);
    }

    public ValidationResult validate(Locale locale, Validator validator, EntityFieldConfig fieldConfig, Object value) {
        return fieldValidator == null ? null : fieldValidator.validate(locale, validator, fieldConfig, value);
    }

}
