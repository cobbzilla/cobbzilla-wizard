package org.cobbzilla.wizard.model.entityconfig;

import org.cobbzilla.wizard.validation.ValidationResult;
import org.cobbzilla.wizard.validation.Validator;

import java.util.Locale;

public interface EntityConfigFieldValidator {

    ValidationResult validate(Locale locale, Validator validator, EntityFieldConfig fieldConfig, Object value);

    Object toObject(Locale locale, String value);

}
