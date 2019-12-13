package org.cobbzilla.wizard.model.entityconfig.validation;

import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.model.entityconfig.EntityConfigFieldValidator;
import org.cobbzilla.wizard.model.entityconfig.EntityFieldConfig;
import org.cobbzilla.wizard.validation.ValidationResult;
import org.cobbzilla.wizard.validation.Validator;

import java.text.DecimalFormat;
import java.util.Locale;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.string.StringUtil.firstWord;

@Slf4j
public class EntityConfigFieldValidator_integer implements EntityConfigFieldValidator {

    @Override public ValidationResult validate(Locale locale, Validator validator, EntityFieldConfig fieldConfig,
                                               Object value) {
        String val = empty(value) ? "" : firstWord(value.toString());
        final DecimalFormat decimalFormat = (DecimalFormat) DecimalFormat.getInstance(locale);
        try {
            if (val.startsWith(decimalFormat.getCurrency().getSymbol())) val = val.substring(1);
            Long.parseLong(val.replace(""+decimalFormat.getDecimalFormatSymbols().getGroupingSeparator(), ""));
        } catch (NumberFormatException e) {
            return new ValidationResult("err."+fieldConfig.getName()+".invalid", "not a valid integer", val);
        }
        return null;
    }

    @Override public Object toObject(Locale locale, String value) {
        if (empty(value)) return null;
        value = firstWord(value);
        final DecimalFormat decimalFormat = (DecimalFormat) DecimalFormat.getInstance(locale);
        try {
            if (value.startsWith(decimalFormat.getCurrency().getSymbol())) value = value.substring(1);
            return Long.parseLong(value.replace(""+decimalFormat.getDecimalFormatSymbols().getGroupingSeparator(), ""));
        } catch (NumberFormatException e) {
            log.error("toObject: invalid integer: "+value);
            return null;
        }
    }

}
