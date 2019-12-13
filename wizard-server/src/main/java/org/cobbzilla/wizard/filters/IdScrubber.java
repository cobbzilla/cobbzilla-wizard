package org.cobbzilla.wizard.filters;

import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.Identifiable;

import javax.ws.rs.ext.Provider;

@Provider
public class IdScrubber extends ResultScrubber {

    public static final ScrubbableField[] SCRUBBABLE_FIELDS = new ScrubbableField[] {
            new ScrubbableField(Identifiable.class, "id", Long.class)
    };

    @Override protected ScrubbableField[] getFieldsToScrub(Object entity) { return SCRUBBABLE_FIELDS; }

}
