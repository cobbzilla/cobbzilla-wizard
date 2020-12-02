package org.cobbzilla.wizard.model.entityconfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.model.entityconfig.validation.*;
import org.cobbzilla.wizard.model.search.SearchFieldType;
import org.cobbzilla.wizard.validation.ValidationResult;
import org.cobbzilla.wizard.validation.Validator;

import javax.persistence.Column;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.wizard.model.Identifiable.*;
import static org.cobbzilla.wizard.model.entityconfig.EntityConfig.fieldNameFromAccessor;

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

    /** valid JSON object (as a string) */
    json (new EntityConfigFieldValidator_json()),

    /** valid JSON array (as a string) */
    json_array (new EntityConfigFieldValidator_json_array()),

    /** a string of characters where comparisons like lt/le/gt/ge are not useful */
    opaque_string (new EntityConfigFieldValidator_string()),

    /** an error/exception string */
    error (new EntityConfigFieldValidator_string()),

    /** a string containing an email address */
    email (new EntityConfigFieldValidator_email()),

    /** an integer-valued number */
    integer (SearchFieldType.integer, new EntityConfigFieldValidator_integer()),

    /** a real number */
    decimal  (SearchFieldType.decimal, new EntityConfigFieldValidator_decimal()),

    /** an integer-valued monetary amount */
    money_integer  (SearchFieldType.integer),

    /** a real-valued monetary amount */
    money_decimal  (SearchFieldType.decimal),

    /** a boolean value */
    flag  (SearchFieldType.flag, new EntityConfigFieldValidator_boolean()),

    /** a date value */
    date  (SearchFieldType.integer),

    /** a date value in the past (before current date) */
    date_past  (SearchFieldType.integer),

    /** a date value in the future (or current date) */
    date_future  (SearchFieldType.integer),

    /** a field for age */
    age  (SearchFieldType.integer),

    /** a 4-digit year field */
    year  (SearchFieldType.integer),

    /** a 4-digit year field that starts with the current year and goes into the past */
    year_past  (SearchFieldType.integer),

    /** a 4-digit year field that starts with the current year and goes into the future */
    year_future  (SearchFieldType.integer),

    /** a 4-digit year and 2-digit month field (YYYY-MM) */
    year_and_month  (SearchFieldType.integer),

    /** a 4-digit year and 2-digit month field (YYYY-MM) field that starts with the current year and goes into the past */
    year_and_month_past  (SearchFieldType.integer),

    /** a 4-digit year and 2-digit month field (YYYY-MM) field that starts with the current year and goes into the future */
    year_and_month_future  (SearchFieldType.integer),

    /** a date or date/time value, represented as milliseconds since 1/1/1970 */
    epoch_time  (SearchFieldType.integer, new EntityConfigFieldValidator_integer()),

    /** a date or date/time value, represented as milliseconds since 1/1/1970 */
    expiration_time  (SearchFieldType.integer, new EntityConfigFieldValidator_integer()),

    /** millisecond value for a time duration */
    time_duration (SearchFieldType.integer, new EntityConfigFieldValidator_integer()),

    /** a time-zone (for example America/New York) */
    time_zone  (),

    /** a locale (for example en_US) */
    locale  (),

    /** a 3-letter currency code (for example USD) */
    currency  (),

    /** an IPv4 address */
    ip4  (),

    /** an IPv6 address */
    ip6  (),

    /** a hostname */
    hostname  (),

    /** a fully-qualified domain name */
    fqdn  (),

    /** a 2-letter US state abbreviation */
    us_state  (),

    /** a US ZIP code */
    us_zip  (),

    /** HTTP URL */
    http_url (new EntityConfigFieldValidator_httpUrl()),

    /** a reference to another EntityConfig instance */
    reference  (),

    /** a base64-encoded PNG image  */
    base64_png  (),

    /** an embedded sub-object */
    embedded  (new EntityConfigFieldValidator_embedded()),

    /** a US phone number */
    us_phone (new EntityConfigFieldValidator_USPhone());

    @Getter private SearchFieldType searchFieldType;
    private EntityConfigFieldValidator fieldValidator;

    EntityFieldType (EntityConfigFieldValidator validator) {
        this.searchFieldType = SearchFieldType.string;
        this.fieldValidator = validator;
    }

    EntityFieldType (SearchFieldType searchFieldType) {
        this.searchFieldType = searchFieldType;
        this.fieldValidator = null;
    }

    EntityFieldType () {
        this.searchFieldType = SearchFieldType.string;
        this.fieldValidator = null;
    }

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
        return guessFieldType(f.getName(), f.getType());
    }

    public static EntityFieldType guessFieldType(Method m) {
        return guessFieldType(fieldNameFromAccessor(m), m.getReturnType());
    }

    public static EntityFieldType guessFieldType(String name, Class<?> type) {
        switch (type.getName()) {
            case "boolean":
            case "java.lang.Boolean":
                return flag;
            case "long":
            case "java.lang.Long":
                if (name.equals(CTIME) || name.equals(MTIME)) return epoch_time;
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
                return string;
            case "java.lang.String":
                if (name.equals(UUID)
                        || name.equals("description")
                        || name.endsWith("Class") || name.endsWith("ClassName")
                        || name.equals("json") || name.endsWith("Json")) return opaque_string;
                if (name.equals(currency.name())) return currency;
                if (name.equals(locale.name())) return locale;
                if (name.equals(fqdn.name())) return fqdn;
                if (name.toLowerCase().endsWith(email.name())) return email;
                if (name.equals(hostname.name()) || name.equals("host") || name.endsWith("Host")) return hostname;
                if (name.equals(time_zone.name()) || name.toLowerCase().equals("timezone")) return time_zone;
                if (name.equals(error.name()) || name.equals("exception")) return error;
                if (name.equals("url")) return http_url;
                return string;
            case "float":
            case "double":
            case "java.lang.Float":
            case "java.lang.Double":
            case "java.math.BigDecimal":
                return decimal;
            default:
                if (type.isEnum()) return string;
                log.warn("guessFieldType: unrecognized type ("+type.getName()+") for field: "+name);
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
