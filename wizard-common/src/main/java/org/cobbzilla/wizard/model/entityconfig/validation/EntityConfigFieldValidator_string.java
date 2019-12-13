package org.cobbzilla.wizard.model.entityconfig.validation;

import org.cobbzilla.wizard.model.entityconfig.EntityConfigFieldValidator;
import org.cobbzilla.wizard.model.entityconfig.EntityFieldConfig;
import org.cobbzilla.wizard.validation.ValidationResult;
import org.cobbzilla.wizard.validation.Validator;

import java.util.Locale;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

public class EntityConfigFieldValidator_string implements EntityConfigFieldValidator {

    @Override public ValidationResult validate(Locale locale, Validator validator, EntityFieldConfig fieldConfig,
                                               Object value) {
        ValidationResult validation = null;
        final String val = empty(value) ? "" : value.toString().trim();
        if (fieldConfig.hasLength() && val.length() > fieldConfig.getLength()) {
            if (validation == null) validation = new ValidationResult();
            validation.addViolation("err."+fieldConfig.getName()+".length");
        }
        return validation;
    }

    @Override public Object toObject(Locale locale, String value) {
        return empty(value) ? "" : value.toString().trim();
    }

}
