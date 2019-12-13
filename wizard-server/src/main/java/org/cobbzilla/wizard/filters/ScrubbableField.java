package org.cobbzilla.wizard.filters;

import lombok.AllArgsConstructor;
import lombok.ToString;

@AllArgsConstructor @ToString(of="name")
public class ScrubbableField {

    public Class targetType;
    public String name;
    public Class type;

}
