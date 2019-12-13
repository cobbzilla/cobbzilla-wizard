package org.cobbzilla.wizard.model.entityconfig;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

/**
 * A wrapper object to hold a single option used by a field whose EntityFieldControl is 'select'
 */
@NoArgsConstructor @AllArgsConstructor @Accessors(chain=true)
public class EntityFieldOption {

    /**
     * Value of the option. This is the value that will be written to the entity field
     */
    @Getter @Setter private String value;
    @Setter private String displayValue;

    /**
     * Display value of the option. This is the value shown in the select list.
     * Default value: the 'value' of the option
     * @return the display value of the option.
     */
    public String getDisplayValue() { return empty(displayValue) ? value : displayValue; }

    public EntityFieldOption(String value) { this(value, value); }

}
