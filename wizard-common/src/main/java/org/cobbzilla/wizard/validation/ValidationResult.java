package org.cobbzilla.wizard.validation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.NoArgsConstructor;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Transformer;
import org.cobbzilla.util.collection.NameAndValue;
import org.cobbzilla.util.string.StringUtil;

import javax.validation.ConstraintViolation;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

// forked from dropwizard-- https://github.com/codahale/dropwizard

@NoArgsConstructor
public class ValidationResult {

    private static final Transformer BEAN_XFORM = input -> new ConstraintViolationBean((ConstraintViolation) input);

    private final AtomicReference<List<ConstraintViolation>> violations = new AtomicReference<>(new ArrayList<>());
    private final AtomicReference<List<ConstraintViolationBean>> beans = new AtomicReference<>(new ArrayList<>());

    public ValidationResult (String violation) { addViolation(violation); }
    public ValidationResult (String violation, String message, String invalidValue) { addViolation(violation, message, invalidValue); }

    public ValidationResult (List<ConstraintViolation> violations) {
        synchronized (this.violations) { this.violations.get().addAll(violations); }
    }

    public static ValidationResult fromMessages(List<String> messages) { return fromMessages(messages, null); }

    public static ValidationResult fromMessages(List<String> messages, String delim) {
        final ValidationResult result = new ValidationResult();
        if (delim == null) delim = "\t\n";
        for (String message : messages) {
            if (empty(message)) continue;
            final List<String> parts = StringUtil.split(message, delim);
            switch (parts.size()) {
                case 0: continue;
                case 1: result.addViolation(message); break;
                case 2: result.addViolation(parts.get(0), parts.get(1)); break;
                case 3: result.addViolation(parts.get(0), parts.get(1), parts.get(2)); break;
                default: result.addViolation(message); break;
            }
        }
        return result;
    }

    @JsonIgnore public List<ConstraintViolation> getViolations() { return violations.get(); }

    public ValidationResult addViolation(ConstraintViolation violation) {
        synchronized (violations) { violations.get().add(violation); }
        return this;
    }
    public ValidationResult addViolation(ConstraintViolationBean violation) {
        synchronized (beans) { beans.get().add(violation); }
        return this;
    }

    public ValidationResult addViolation(String messageTemplate) {
        return addViolation(messageTemplate, null, null);
    }

    public ValidationResult addViolation(String messageTemplate, String message) {
        return addViolation(messageTemplate, message, null);
    }

    public ValidationResult addViolation(String messageTemplate, String message, String invalidValue) {
        return addViolation(messageTemplate, message, invalidValue, null);
    }

    public ValidationResult addViolation(String messageTemplate, String message, String invalidValue,
                                         NameAndValue[] params) {
        final ConstraintViolationBean err = new ConstraintViolationBean(messageTemplate, message, invalidValue, params);
        synchronized (beans) {
            for (ConstraintViolationBean bean : beans.get()) {
                if (bean.equals(err)) {
                    return this;
                }
            }
            beans.get().add(err);
        }
        return this;
    }

    public void addAll(ConstraintViolationBean[] violations) {
        if (!empty(violations)) {
            for (ConstraintViolationBean v : violations) addViolation(v);
        }
    }

    public void addAll(ValidationResult result) {
        if (result == null) return;
        for (ConstraintViolationBean violationBean : result.getViolationBeans()) {
            addViolation(violationBean);
        }
    }

    public List<ConstraintViolationBean> getViolationBeans() {
        final List<ConstraintViolationBean> beanList = (List<ConstraintViolationBean>) CollectionUtils.collect(violations.get(), BEAN_XFORM);
        beanList.addAll(beans.get());
        return beanList;
    }
    public void setViolationBeans (List<ConstraintViolationBean> beans) {
        synchronized (this.beans) { this.beans.set(beans == null ? new ArrayList<>() : beans); }
    }

    @JsonIgnore public boolean isValid () { return isEmpty(); }
    @JsonIgnore public boolean isInvalid () { return !isEmpty(); }
    @JsonIgnore public boolean isEmpty () { return violations.get().isEmpty() && beans.get().isEmpty(); }

    public boolean hasFieldError(String name) {
        for (ConstraintViolationBean bean : getViolationBeans()) {
            final String field = ConstraintViolationBean.getField(bean.getMessageTemplate());
            if (field != null && name.equals(field)) return true;
        }
        return false;
    }

    public boolean hasInvalidValue (String value) {
        for (ConstraintViolationBean bean : getViolationBeans()) {
            if (bean.hasInvalidValue() && bean.getInvalidValue().equals(value)) return true;
        }
        return false;
    }

    @Override public String toString() { return violations.get().toString() + (beans.get().isEmpty() ? "" : ", "+beans.get().toString()); }

    public ValidationErrors errors() { return new ValidationErrors(this.getViolationBeans()); }

    public int violationCount() { return isEmpty() ? 0 : getViolationBeans().size(); }

}
