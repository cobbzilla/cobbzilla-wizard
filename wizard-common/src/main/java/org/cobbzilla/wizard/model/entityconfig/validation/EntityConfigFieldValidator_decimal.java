package org.cobbzilla.wizard.model.entityconfig.validation;

import org.cobbzilla.wizard.model.entityconfig.EntityConfigFieldValidator;
import org.cobbzilla.wizard.model.entityconfig.EntityFieldConfig;
import org.cobbzilla.wizard.validation.ValidationResult;
import org.cobbzilla.wizard.validation.Validator;

import java.util.Locale;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

public class EntityConfigFieldValidator_decimal implements EntityConfigFieldValidator {

    @Override public ValidationResult validate(Locale locale, Validator validator, EntityFieldConfig fieldConfig,
                                               Object value) {
        return null;
    }

    @Override public Object toObject(Locale locale, String value) {
        return empty(value) ? null : Double.parseDouble(value);
    }

}
