package org.cobbzilla.wizard.validation;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

@NoArgsConstructor @AllArgsConstructor
public class ConstraintViolationList {

    @Getter @Setter private ConstraintViolationBean[] violations;

    public int size () { return empty(violations) ? 0 : violations.length; }

    public boolean hasError (String messageTemplate) {
        if (!empty(violations)) {
            for (ConstraintViolationBean violation : violations) {
                if (violation.getMessageTemplate().equals(messageTemplate)) return true;
            }
        }
        return false;
    }

    public boolean hasError (String messageTemplate, String invalidValue) {
        if (!empty(violations)) {
            for (ConstraintViolationBean violation : violations) {
                if (violation.getMessageTemplate().equals(messageTemplate)
                        && violation.hasInvalidValue() && violation.getInvalidValue().equals(invalidValue)) {
                    return true;
                }
            }
        }
        return false;
    }

    // convenience methods
    public boolean hasValidationError (String messageTemplate) { return hasError(messageTemplate); }
    public boolean hasValidationError (String messageTemplate, String invalidValue) { return hasError(messageTemplate, invalidValue); }

}
