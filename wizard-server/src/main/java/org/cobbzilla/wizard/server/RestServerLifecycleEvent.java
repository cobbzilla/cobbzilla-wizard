package org.cobbzilla.wizard.server;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum RestServerLifecycleEvent {

    beforeStart, onStart, beforeStop, onStop;

    @JsonCreator public static RestServerLifecycleEvent fromString (String val) { return valueOf(val); }

}
