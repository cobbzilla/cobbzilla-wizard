package org.cobbzilla.wizard.filters;

import javax.ws.rs.ext.Provider;
import java.util.Collection;

@Provider
public class ScrubbableScrubber extends ResultScrubber {

    public static final ScrubbableField[] NOTHING_TO_SCRUB = new ScrubbableField[0];

    @Override protected ScrubbableField[] getFieldsToScrub(Object entity) {
        if (entity instanceof Scrubbable) {
            return ((Scrubbable) entity).fieldsToScrub();

        } else if (entity instanceof Collection) {
            final Collection c = (Collection) entity;
            if (c.isEmpty()) return NOTHING_TO_SCRUB;
            final Object first = c.iterator().next();
            if (first instanceof Scrubbable) return ((Scrubbable) first).fieldsToScrub();

        } else if (entity instanceof Object[]) {
            final Object[] a = (Object[]) entity;
            if (a.length == 0) return NOTHING_TO_SCRUB;
            if (a[0] instanceof Scrubbable) return ((Scrubbable) a[0]).fieldsToScrub();
        }
        return NOTHING_TO_SCRUB;
    }

}
