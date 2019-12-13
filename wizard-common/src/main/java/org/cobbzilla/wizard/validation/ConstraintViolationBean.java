package org.cobbzilla.wizard.validation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.NameAndValue;
import org.cobbzilla.util.json.JsonUtil;

import javax.validation.ConstraintViolation;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

@XmlRootElement @AllArgsConstructor @NoArgsConstructor @ToString @Slf4j
public class ConstraintViolationBean {

    public static final ConstraintViolationBean[] EMPTY_VIOLATION_ARRAY = new ConstraintViolationBean[0];

    @XmlElement @Getter @Setter private String messageTemplate;
    @XmlElement @Getter @Setter private String message;
    @XmlElement @Getter @Setter private String invalidValue;
    @XmlElement @Getter @Setter private NameAndValue[] params;
    public boolean hasInvalidValue () { return !empty(invalidValue); }

    public ConstraintViolationBean(String messageTemplate) {
        this(messageTemplate, messageTemplate, null, null);
    }

    public ConstraintViolationBean(String messageTemplate, String message) {
        this(messageTemplate, message, null, null);
    }

    public ConstraintViolationBean(String messageTemplate, String message, String invalidValue) {
        this(messageTemplate, message, invalidValue, null);
    }

    public ConstraintViolationBean(ConstraintViolation violation) {
        this.messageTemplate = violation.getMessageTemplate();
        this.message = violation.getMessage();
        try {
            Object val = violation.getInvalidValue();
            this.invalidValue = (val == null) ? "none-set" : val.toString();
        } catch (Exception e) {
            this.invalidValue = "Error converting invalid value to String: "+e;
        }
    }

    public static List<ConstraintViolationBean> fromJsonArray(String json) {
        return empty(json)
                ? (List<ConstraintViolationBean>) Collections.EMPTY_LIST
                : Arrays.asList(JsonUtil.fromJsonOrDie(json, ConstraintViolationBean[].class));
    }

    @JsonIgnore public String getField () { return getField(messageTemplate); }

    public static String getField (String messageTemplate) {
        final int firstDot = messageTemplate.indexOf('.');
        final int lastDot = messageTemplate.lastIndexOf('.');
        try {
            return (firstDot != -1 && lastDot != -1 && firstDot != lastDot)
                    ? messageTemplate.substring(firstDot + 1, lastDot)
                    : null;
        } catch (Exception e) {
            log.info("getField("+messageTemplate+"): ", e);
            return null;
        }
    }
}
