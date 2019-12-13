package org.cobbzilla.wizard.util;

import lombok.*;
import lombok.experimental.Accessors;
import org.cobbzilla.util.collection.NameAndValue;

@NoArgsConstructor @AllArgsConstructor @Accessors(chain=true) @ToString
public class RestResponseHeader {

    @Getter @Setter private String name;
    @Getter @Setter private String value;

    public RestResponseHeader(NameAndValue h) {
        setName(h.getName());
        setValue(h.getValue());
    }

}
