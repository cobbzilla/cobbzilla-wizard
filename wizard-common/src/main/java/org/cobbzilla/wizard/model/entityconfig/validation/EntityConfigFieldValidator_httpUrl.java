package org.cobbzilla.wizard.model.entityconfig.validation;

import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.http.URIUtil;
import org.cobbzilla.wizard.model.entityconfig.EntityConfigFieldValidator;
import org.cobbzilla.wizard.model.entityconfig.EntityFieldConfig;
import org.cobbzilla.wizard.validation.ValidationResult;
import org.cobbzilla.wizard.validation.Validator;

import java.net.URI;
import java.util.Locale;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

@Slf4j
public class EntityConfigFieldValidator_httpUrl implements EntityConfigFieldValidator {

    @Override public ValidationResult validate(Locale locale, Validator validator, EntityFieldConfig fieldConfig,
                                               Object value) {
        try {
            URI uri = URIUtil.toUri(value.toString());
            if (uri.getScheme().equalsIgnoreCase("http") || uri.getScheme().equalsIgnoreCase("https")) return null;
        } catch (Exception e) {
            log.warn("validate: "+e);
        }
        return new ValidationResult("err."+fieldConfig.getName()+".invalid", "Not a valid HTTP URL", value.toString());
    }

    @Override public Object toObject(Locale locale, String value) {
        if (empty(value)) return null;
        return value.trim();
    }

}
