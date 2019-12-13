package org.cobbzilla.wizard.model.search;

import java.util.List;

public interface CustomSearchBoundProcessor {

    String getOperation();

    String sql(SearchField searchField, SearchBound bound, String value, List<Object> params);

}
