package org.cobbzilla.wizard.model.search;

public interface SearchBoundValueFunction {

    Object paramValue(SearchBound bound, String value);

}
