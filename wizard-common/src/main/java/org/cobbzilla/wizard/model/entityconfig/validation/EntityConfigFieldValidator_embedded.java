package org.cobbzilla.wizard.model.entityconfig.validation;

import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.model.entityconfig.EntityConfigFieldValidator;
import org.cobbzilla.wizard.model.entityconfig.EntityFieldConfig;
import org.cobbzilla.wizard.validation.ValidationResult;
import org.cobbzilla.wizard.validation.Validator;

import java.util.Locale;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

@Slf4j
public class EntityConfigFieldValidator_embedded implements EntityConfigFieldValidator {

    @Override public ValidationResult validate(Locale locale, Validator validator, EntityFieldConfig fieldConfig,
                                               Object value) {
        if (validator == null) {
            return new ValidationResult("err.validation.unsupported", "Cannot validate field without validator object",
                                        fieldConfig.getName());
        }
        return !empty(value) ? validator.validate(value) : null;
    }

    @Override public Object toObject(Locale locale, String value) {
        log.error("invalid call: toObject method not supported for embedded fields");
        return null;
    }
}
