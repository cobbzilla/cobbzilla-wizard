package org.cobbzilla.wizard.model.entityconfig.validation;

import com.fasterxml.jackson.databind.JsonNode;
import org.cobbzilla.wizard.model.entityconfig.EntityFieldConfig;
import org.cobbzilla.wizard.validation.ValidationResult;
import org.cobbzilla.wizard.validation.Validator;

import java.util.Locale;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;
import static org.cobbzilla.util.json.JsonUtil.json;

public class EntityConfigFieldValidator_json extends EntityConfigFieldValidator_string {

    @Override public ValidationResult validate(Locale locale, Validator validator, EntityFieldConfig fieldConfig,
                                               Object value) {
        final ValidationResult validation = super.validate(locale, validator, fieldConfig, value);
        if (validation != null && validation.isInvalid()) return validation;
        final String val = empty(value) ? "" : value.toString().trim();
        if (empty(val)) return fieldConfig.required() ? new ValidationResult("err."+fieldConfig.getName()+".required") : null;
        try {
            json(value.toString(), getJsonClass());
        } catch (Exception e) {
            return new ValidationResult("err."+fieldConfig.getName()+".invalid", "Error converting to JSON: "+shortError(e), ""+value);
        }
        return validation;
    }

    public Class<? extends JsonNode> getJsonClass() { return JsonNode.class; }

    @Override public Object toObject(Locale locale, String value) {
        return empty(value) ? "" : value.toString().trim();
    }

}
