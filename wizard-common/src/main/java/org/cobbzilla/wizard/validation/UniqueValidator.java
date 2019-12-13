package org.cobbzilla.wizard.validation;

import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.reflect.ReflectionUtil;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Service;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service @Slf4j
public class UniqueValidator implements ConstraintValidator<IsUnique, Object>, ApplicationContextAware {

    private String uniqueProperty;
    private String uniqueField;
    private String idProperty;
    private String idField;
    private String daoBean;

    private static ApplicationContext applicationContext;

    @Override public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        UniqueValidator.applicationContext = applicationContext;
    }

    @Override public void initialize(IsUnique constraintAnnotation) {
        this.uniqueProperty = constraintAnnotation.unique();
        this.uniqueField = constraintAnnotation.uniqueField();
        this.idProperty = constraintAnnotation.id();
        this.idField = constraintAnnotation.idField();
        this.daoBean = constraintAnnotation.daoBean();
    }

    private Map<String, Boolean> checking = new ConcurrentHashMap<>();

    @Override public boolean isValid(Object object, ConstraintValidatorContext context) {

        if (object == null) return false;
        final String hash = object.getClass().getName()+"-"+object.hashCode();
        if (checking.containsKey(hash)) return true; // someone else is already checking this
        try {
            checking.put(hash, true);

            UniqueValidatorDao dao = (UniqueValidatorDao) applicationContext.getBean(daoBean);

            Object idValue = (idProperty.equals(IsUnique.CREATE_ONLY) || !ReflectionUtil.hasGetter(object, idProperty)) ? null : ReflectionUtil.get(object, idProperty);
            final Object fieldValue;
            try {
                fieldValue = ReflectionUtil.get(object, uniqueProperty);
            } catch (Exception e) {
                log.warn("uniqueProperty (" + uniqueProperty + ") couldn't be read from target object " + object + ": " + e, e);
                return true;
            }

            if (uniqueField.equals(IsUnique.DEFAULT)) uniqueField = uniqueProperty;
            if (idField.equals(IsUnique.DEFAULT)) idField = idProperty;

            if (idValue == null) {
                return dao.isUnique(uniqueField, fieldValue);
            }
            return dao.isUnique(uniqueField, fieldValue, idField, idValue);

        } finally {
            checking.remove(hash);
        }
    }

}
