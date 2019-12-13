package org.cobbzilla.wizard.client.script;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ApiInnerScriptRunMode {

    fail_fast, run_all, run_all_verbose_errors;

    @JsonCreator public static ApiInnerScriptRunMode fromString (String val) { return valueOf(val.toLowerCase()); }

}
