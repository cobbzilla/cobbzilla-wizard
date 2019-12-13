package org.cobbzilla.wizard.dao;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

@Accessors(chain=true)
public class DebugSearchQuery {

    @Getter @Setter private String source;
    public boolean hasSource () { return !empty(source); }

    @Getter @Setter private String query;
    @Getter @Setter private String filter;
    @Getter @Setter private int from = 0;
    @Getter @Setter private int maxResults = 100;

    // Full class name of a class that implements SearchPreparer
    @Getter @Setter private String searchPreparer = null;
    public boolean hasSearchPreparer () { return !empty(searchPreparer); }
}
