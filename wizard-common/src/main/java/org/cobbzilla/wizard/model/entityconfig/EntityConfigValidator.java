package org.cobbzilla.wizard.model.entityconfig;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.cobbzilla.wizard.validation.ValidationResult;
import org.cobbzilla.wizard.validation.Validator;

@AllArgsConstructor
public class EntityConfigValidator extends Validator {

    @Getter @Setter private EntityConfigSource entityConfigSource;

    @Override public ValidationResult validate(Object o, Class<?>[] groups) {
        final ValidationResult validation = super.validate(o, groups);
        if (entityConfigSource != null) {
            final EntityConfig entityConfig = entityConfigSource.getEntityConfig(o);
            if (entityConfig != null) {
                validation.addAll(entityConfig.validate(this, o));
            }
        }
        return validation;
    }
}
