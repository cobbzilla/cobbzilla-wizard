package org.cobbzilla.wizard.server.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import static org.cobbzilla.util.reflect.ReflectionUtil.copy;

@NoArgsConstructor
public class HibernateConfiguration {

    @Getter @Setter private String[] entityPackages;

    @Getter @Setter private String dialect;
    @Getter @Setter private Boolean showSql = false;
    public boolean showSql() { return showSql != null && showSql; }

    @Getter @Setter private String hbm2ddlAuto;
    @Getter @Setter private String validationMode;
    @Getter @Setter private boolean applyValidatorToDDL = true;

    public HibernateConfiguration(HibernateConfiguration other) { copy(this, other); }

}
