package org.cobbzilla.wizard.validation;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.experimental.Delegate;

import javax.persistence.Transient;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;

@NoArgsConstructor @AllArgsConstructor @Accessors(chain=true)
public class ValidationErrors implements List<ConstraintViolationBean> {

    @Transient @Getter private final long ctime = now();

    @Transient @Getter @Setter @Delegate private List<ConstraintViolationBean> errors = new ArrayList<>();

    public ValidationErrors (ConstraintViolationBean[] violations) { errors.addAll(Arrays.asList(violations)); }

    public ConstraintViolationBean err(String messageTemplate) {
        if (!empty(errors)) {
            for (ConstraintViolationBean violationBean : errors) {
                if (violationBean.getMessageTemplate().equalsIgnoreCase(messageTemplate)) return violationBean;
            }
        }
        return null;
    }

    public boolean has(String messageTemplate) { return err(messageTemplate) != null; }

    public boolean has(String messageTemplate, String invalidValid) {
        if (!empty(errors)) {
            for (ConstraintViolationBean violationBean : errors) {
                if (violationBean.getMessageTemplate().equalsIgnoreCase(messageTemplate)
                        && violationBean.hasInvalidValue() && violationBean.getInvalidValue().equals(invalidValid)) {
                    return true;
                }
            }
        }
        return false;
    }

    public ConstraintViolationBean getError(String messageTemplate) {
        for (ConstraintViolationBean c : errors) if (c.getMessageTemplate().equals(messageTemplate)) return c;
        return null;
    }

    public ValidationResult toValidationResult () {
        final ValidationResult validation = new ValidationResult();
        for (ConstraintViolationBean error : getErrors()) validation.addViolation(error);
        return validation;
    }
}
