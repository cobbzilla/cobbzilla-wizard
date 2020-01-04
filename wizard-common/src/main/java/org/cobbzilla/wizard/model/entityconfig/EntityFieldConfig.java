package org.cobbzilla.wizard.model.entityconfig;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.validation.ValidationResult;
import org.cobbzilla.wizard.validation.Validator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.json.JsonUtil.json;
import static org.cobbzilla.util.reflect.ReflectionUtil.copy;
import static org.cobbzilla.util.string.StringUtil.camelCaseToString;

/**
 * Defines how to work with a particular field defined in an EntityConfig
 */
@Slf4j @Accessors(chain=true) @ToString
public class EntityFieldConfig implements VerifyLogAware<EntityFieldConfig> {

    public static EntityFieldConfig field(String name) { return new EntityFieldConfig().setName(name); } // convenience method

    /**
     * The name of the field. This field is optional when declaring configs via JSON, it will be populated with the key name
     * used in the EntityConfig's `fields` map.
     */
    @Getter @Setter private String name;

    /**
     * The order the field should appear in when viewing a single object. Lower indexes are shown first.
     */
    @Getter @Setter private Integer index;

    @Setter private String displayName;
    /**
     * The display name of the field.
     * Default value: the value of the `name` field
     * @return The display name of the field
     */
    public String getDisplayName() { return !empty(displayName) ? displayName : camelCaseToString(name); }

    /**
     * The mode of the field. Allowed modes are 'standard', 'createOnly', 'readOnly'
     */
    @Getter @Setter private EntityFieldMode mode = EntityFieldMode.standard;

    /**
     * The data type of the field.
     */
    @Getter @Setter private EntityFieldType type = null;
    @JsonIgnore public EntityFieldType getTypeOrDefault () { return hasType() ? getType() : EntityFieldType.string; }
    public boolean hasType() { return !empty(type); }

    /**
     * For data types like 'string', this is the length of the field.
     */
    @Getter @Setter private Integer length = null;
    public boolean hasLength () { return length != null && length >= 0; }

    @Setter private EntityFieldControl control;
    /**
     * The preferred kind of UI control to use when displaying the field.
     */
    public EntityFieldControl getControl() {
        if (control != null && control != EntityFieldControl.unset) return control;
        switch (getTypeOrDefault()) {

            case flag:                  return EntityFieldControl.flag;

            case date_future: case date_past:
            case epoch_time: case date: return EntityFieldControl.date;

            case year:           case year_future:           case year_past:
            case age:                   return EntityFieldControl.select;

            case year_and_month: case year_and_month_future: case year_and_month_past:
                                        return EntityFieldControl.year_and_month;

            default:                    return defaultTextControl();
        }
    }

    public EntityFieldControl defaultTextControl() {
        return hasLength() && length > 200 ? EntityFieldControl.textarea : EntityFieldControl.text;
    }

    /**
     * When the value of the 'control' field is 'EntityFieldControl.select', this determines the source of the options
     * in the select list. It can be: <ul><li>
     * a comma-separated string of values
     * </li><li>JSON representing an array of EntityFieldOption objects
     * </li><li>a special string 'uri:api-path:value:displayValue', this means:<ul><li>
     *        </li><li> do a GET of api-path
     *        </li><li> expect the response to be a JSON array of objects
     *        </li><li> for each object in the array, use the 'value' field for the option value, and the 'displayValue' field for the option's display value
     *      </li></ul>
     * </li></ul>
     */
    @Getter @Setter private String options;

    /** the value of the special (usually first-listed) option that indicates no selection has been made */
    @Getter @Setter private String emptyDisplayValue;

    public EntityFieldConfig setOptionsList(EntityFieldOption[] options) {
        this.options = json(options);
        return this;
    }

    /**
     * Get the options as a list. This assumes that options is set of comma-separated values. URI-based options will return null.
     * @return An array of EntityFieldOptions, or null if there were no options or options were URI-based
     */
    public EntityFieldOption[] getOptionsList() {

        if (empty(options)) return null;

        if (options.startsWith("uri:")) {
            log.debug("getOptionsArray: cannot convert uri-style options to array: "+options);
            return null;
        }

        if (options.trim().startsWith("[")) {
            return json(options, EntityFieldOption[].class);
        } else {
            final List<EntityFieldOption> opts = new ArrayList<>();
            for (String opt : options.split(",")) opts.add(new EntityFieldOption(opt.trim()));
            return opts.toArray(new EntityFieldOption[opts.size()]);
        }
    }

    /**
     * When the value of 'type' is 'reference', this provides details about how to find the referenced object.
     */
    @Getter @Setter private EntityFieldReference reference = null;
    @JsonIgnore public boolean isParentReference () {
        return getType() == EntityFieldType.reference && getReference().getEntity().equals(EntityFieldReference.REF_PARENT);
    }

    /**
     * When the value of 'type' is 'embedded', this is the name of the EntityConfig to use when working with the embedded object.
     */
    @Getter @Setter private String objectType;

    @Override public EntityFieldConfig beforeDiff(EntityFieldConfig thing, Map<String, Identifiable> context, Object resolver) {
        final EntityFieldConfig e = new EntityFieldConfig();
        copy(e, thing);
        e.setType(e.getTypeOrDefault());
        return e;
    }

    public ValidationResult validate(Locale locale, Validator validator, Object o) {
        return getTypeOrDefault().validate(locale, validator, this, o);
    }

    public String displayValueFor(String answer) {

        if (!getControl().hasDisplayValues()) return answer;

        final EntityFieldOption[] optionsList = getOptionsList();
        if (empty(optionsList)) return answer;

        switch (getControl()) {
            case select:
                for (EntityFieldOption option : optionsList) {
                    if (option.getValue().equals(answer)) return option.getDisplayValue();
                }
                return answer;

            case multi_select:
                final StringBuilder b = new StringBuilder();
                for (String val : answer.split(",")) {
                    if (b.length() > 0) b.append(",");
                    val = val.trim();
                    boolean found = false;
                    for (EntityFieldOption option : optionsList) {
                        if (option.getValue().equals(val)) {
                            b.append(option.getDisplayValue());
                            found = true;
                        }
                    }
                    if (!found) b.append(answer);
                }
                return b.toString();

            default:
                return die("displayValueFor("+answer+"): unsupported control type: "+getControl());
        }
    }
}
