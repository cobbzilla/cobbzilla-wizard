package org.cobbzilla.wizard.model.context;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@NoArgsConstructor @AllArgsConstructor @Accessors(chain=true)
public class ContextEntry {

    @Getter @Setter private String name;
    @Getter @Setter private String className;
    @Getter @Setter private String json;

}
