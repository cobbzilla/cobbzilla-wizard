package org.cobbzilla.wizard.model.entityconfig;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Specifies which kind of UI control should be used.
 */
public enum EntityFieldControl {

    /** instead of using null - constant required in annotation definition */
    unset,

    /** a standard text input field */
    text,

    /** a multi-line text input field */
    textarea,

    /** a large multi-line text input field */
    big_textarea,

    /** a yes/no field that also supports a 'no selection made' state */
    flag,

    /** select one item from a list */
    select,

    /** select multiple items from a list */
    multi_select,

    /** a date field (calendar) */
    date,

    /** a date and time field (calendar + time selection) */
    date_and_time,

    /** a year and month field (two drop-down boxes) */
    year_and_month,

    /** an auto-complete text field  */
    auto,

    /** a strict auto-complete field */
    auto_strict,

    /** an auto-complete text field (WIP) */
    autocomplete,

    /** a duration text field - for example 7d for 7 days, or a millisecond duration value */
    duration,

    /** a hidden field (do not display to user) */
    hidden,

    /** a label field (read-only display) */
    label;

    /** Jackson-hook to create a new instance based on a string, case-insensitively */
    @JsonCreator public static EntityFieldControl create (String val) { return valueOf(val.toLowerCase()); }

    public boolean hasDisplayValues() { return this == select || this == multi_select; }

}
