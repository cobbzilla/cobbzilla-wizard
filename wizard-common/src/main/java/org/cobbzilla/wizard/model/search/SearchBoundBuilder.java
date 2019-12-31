package org.cobbzilla.wizard.model.search;

import org.cobbzilla.wizard.model.search.SearchBound;

import java.util.List;

public interface SearchBoundBuilder {

    SearchBound[] build(String bound, String value, List<Object> params, String locale);

}
