package org.cobbzilla.wizard.model.search;

import org.cobbzilla.wizard.validation.SimpleViolationException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.wizard.model.search.SearchBoundComparison.*;

public interface SearchField {

    String OP_SEP = ":";

    static <SF extends SearchField> SF fromString (String val, SF[] values) {
        for (SF f : values) {
            if (f.name().equalsIgnoreCase(val)) return f;
        }
        throw invalid("err.searchField.invalid", "not a valid search field", val);
    }

    static SearchBound[] bindTime(String name) { return new SearchBound[] { during.bind(name), after.bind(name), before.bind(name) }; }
    static SearchBound[] bindNullable(String name) { return new SearchBound[] { eq.bind(name), is_null.bind(name), not_null.bind(name) }; }

    String name();

    SearchBound[] getBounds();
    default boolean hasBounds() { return !empty(getBounds()); }

    List<String> getComparisons();

    String getSort();
    default boolean hasSort() { return !empty(getSort()); }

    default SearchBound getBound(SearchBoundComparison comparison) {
        if (hasBounds()) for (SearchBound b : getBounds()) if (b.getComparison() == comparison) return b;
        return null;
    }

    default SearchBound getCustomBound(String op) {
        if (hasBounds()) for (SearchBound b : getBounds()) if (b.getComparison() == custom && b.getProcessor().getOperation().equals(op)) return b;
        return null;
    }

    default List<String> initComparisons() {
        final List<String> list = new ArrayList<>();
        if (hasBounds()) for (SearchBound b : getBounds()) list.add(b.getComparison().name());
        return list;
    }

    static List<String> initSortFields(SearchField[] values) {
        final List<String> fields = new ArrayList<>();
        for (SearchField f : values) if (f.hasSort()) fields.add(f.name());
        return fields;
    }

    static Map<String, SearchBound[]> initBounds(SearchField[] values) {
        final Map<String, SearchBound[]> fields = new HashMap<>();
        for (SearchField f : values) if (f.hasBounds()) {
            fields.put(f.name(), copyBoundsWithoutNames(f));
        }
        return fields;
    }

    static SearchBound[] copyBoundsWithoutNames(SearchField f) {
        final SearchBound[] bounds = f.getBounds();
        final SearchBound[] copy = new SearchBound[bounds.length];
        for (int i=0; i<bounds.length; i++) copy[i] = new SearchBound(bounds[i]).setName(null);
        return copy;
    }

    static String buildBound(SearchField field, String value, List<Object> params) {
        final String bound = field.name();
        if (!field.hasBounds()) throw invalid("err.bound.invalid", "bind is not valid", bound);

        SearchBoundComparison comparison;
        final SearchBound searchBound;
        final int cPos = value.indexOf(OP_SEP);
        if (cPos != -1) {
            final String[] parts = value.split(OP_SEP);
            final String comparisonName = parts[0];
            if (comparisonName.startsWith(SearchBoundComparison.custom.name())) {
                // custom comparison
                if (parts.length <= 2) throw invalid("err.bound.custom.invalid", "custom bound was missing argument", value);
                searchBound = field.getCustomBound(parts[1]);
                return searchBound.getProcessor().sql(field, searchBound, parts[2], params);

            } else {
                // standard comparison
                comparison = SearchBoundComparison.fromStringOrNull(comparisonName);
                if (comparison != null) {
                    searchBound = field.getBound(comparison);
                    if (searchBound == null) {
                        throw invalid("err.bound.operation.invalid", "invalid comparison for bound " + bound + ": " + comparisonName, comparisonName);
                    }
                    value = value.substring(cPos + OP_SEP.length());
                } else {
                    // whoops, might just be single value that contains a colon, try that
                    searchBound = field.getBounds()[0];
                    comparison = searchBound.getComparison();
                    if (comparison.isCustom()) throw invalid("err.bound.custom.invalid", "custom bound was missing argument", value);
                }
            }

        } else {
            // no comparison specified, use first and assume default
            searchBound = field.getBounds()[0];
            comparison = searchBound.getComparison();
            if (comparison.isCustom()) throw invalid("err.bound.custom.invalid", "custom bound was missing argument", value);
        }

        return comparison.sql(searchBound, params, value);
    }

    static SimpleViolationException invalid(String messageTemplate, String message, String invalidValue) {
        return new SimpleViolationException(messageTemplate, message, invalidValue);
    }

}
