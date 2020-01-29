package org.cobbzilla.wizard.model.search;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.cobbzilla.util.collection.ArrayUtil;
import org.cobbzilla.util.collection.NameAndValue;
import org.cobbzilla.util.json.JsonUtil;
import org.cobbzilla.util.string.StringUtil;

import java.util.Arrays;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.wizard.model.Identifiable.CTIME;

@NoArgsConstructor @Accessors(chain=true) @ToString
public class SearchQuery {

    public static final String PARAM_USE_PAGINATION = "page";
    public static final String PARAM_PAGE_NUMBER    = "pn";
    public static final String PARAM_PAGE_SIZE      = "ps";
    public static final String PARAM_SORT_FIELD     = "sf";
    public static final String PARAM_SORT_ORDER     = "so";
    public static final String PARAM_FILTER         = "q";
    public static final String PARAM_BOUNDS         = "b";

    public static final int MAX_FILTER_LENGTH = 50;
    public static final int MAX_SORTFIELD_LENGTH = 50;
    public static final String DEFAULT_SORT_FIELD = CTIME;

    public static final SearchQuery DEFAULT_PAGE = new SearchQuery();
    public static final SearchQuery FIRST_RESULT = new SearchQuery(1, 1);
    public static final int INFINITE = Integer.MAX_VALUE;
    public static final SearchQuery INFINITE_PAGE = new SearchQuery(1, INFINITE);

    public static final SearchQuery EMPTY_PAGE = new SearchQuery(1, 0);
    public static final SearchQuery LARGE_PAGE = new SearchQuery(1, 100);

    // for using ResultPage as a query-parameter
    public static SearchQuery valueOf (String json) throws Exception {
        return JsonUtil.fromJson(json, SearchQuery.class);
    }

    public SearchQuery(SearchQuery other) {
        this.setPageNumber(other.getPageNumber());
        this.setPageSize(other.getPageSize());
        this.setFilter(other.getFilter());
        this.setSorts(other.getSorts());
        this.setBounds(other.getBounds());
    }

    public SearchQuery(Integer pageNumber, Integer pageSize, String sortField, String sortOrder, String filter, NameAndValue[] bounds) {
        if (pageNumber != null) setPageNumber(pageNumber);
        if (pageSize != null) setPageSize(pageSize);
        if (sortField != null) {
            addSort(new SearchSort(sortField, SortOrder.fromString(sortOrder)));
        }
        if (filter != null) this.filter = filter;
        this.bounds = bounds;
    }

    public SearchQuery(int pageNumber, int pageSize) {
        this(pageNumber, pageSize, null, normalizeSortOrder(null), null);
    }

    public SearchQuery(int pageNumber, int pageSize, String sortField, SortOrder sortOrder) {
        this(pageNumber, pageSize, sortField, sortOrder, null);
    }

    public SearchQuery(int pageNumber, int pageSize, String sortField, SortOrder sortOrder, String filter) {
        this(pageNumber, pageSize, sortField, normalizeSortOrder(sortOrder), filter, null);
    }

    public SearchQuery(int pageNumber, int pageSize, String sortField, String sortOrder, String filter) {
        this(pageNumber, pageSize, sortField, sortOrder, filter, null);
    }

    private static String normalizeSortOrder(SortOrder sortOrder) {
        return (sortOrder == null) ? SortOrder.DEFAULT_SORT : sortOrder.name();
    }

    public static SearchQuery singleResult (String sortField, SortOrder sortOrder) {
        return new SearchQuery(1, 1, sortField, sortOrder);
    }

    public static SearchQuery singleResult (String sortField, SortOrder sortOrder, String filter) {
        return new SearchQuery(1, 1, sortField, sortOrder, filter);
    }

    @Getter private int pageNumber = 1;
    public SearchQuery setPageNumber(int pageNumber) { this.pageNumber = pageNumber <= 0 ? 1 : pageNumber; return this; }

    @Getter private int pageSize = 10;
    public SearchQuery setPageSize(int pageSize) { this.pageSize = pageSize <= 0 ? 10 : pageSize; return this; }

    @JsonIgnore public int getPageOffset () { return (getPageNumber()-1) * pageSize; }
    public boolean containsResult(int i) { return (i >= getPageOffset() && i <= getPageOffset()+getPageSize()); }

    @JsonIgnore public boolean isInfinitePage () { return pageSize == INFINITE_PAGE.pageSize; }
    @JsonIgnore public int getPageEndOffset() {
        return isInfinitePage() ? INFINITE_PAGE.pageSize : getPageOffset() + getPageSize();
    }

    public SearchQuery setReturnAllResults () { setPageNumber(INFINITE_PAGE.pageNumber); setPageSize(INFINITE_PAGE.pageSize); return this; }

    public static final int MAX_PAGE_BUFFER = 100;
    @JsonIgnore public int getPageBufferSize () {
        return isInfinitePage() || pageSize > MAX_PAGE_BUFFER ? MAX_PAGE_BUFFER : pageSize;
    }

    @Getter @Setter private SearchSort[] sorts;
    @JsonIgnore public boolean hasSorts() { return !empty(sorts); }
    public boolean hasSort(String field) { return !empty(sorts) && Arrays.stream(sorts).anyMatch(s -> s.getSortField().equals(field)); }
    public SearchQuery addSort(SearchSort sort) {
        if (sorts == null) {
            sorts = new SearchSort[] {sort};
        } else {
            sorts = ArrayUtil.append(sorts, sort);
        }
        return this;
    }

    @Setter private String filter = null;
    public String getFilter() {
        // only return the first several chars, to thwart a hypothetical injection attack.
        return StringUtil.prefix(filter, MAX_FILTER_LENGTH);
    }
    @JsonIgnore public boolean getHasFilter() { return filter != null && filter.trim().length() > 0; }

    @Getter @Setter private NameAndValue[] bounds;
    @JsonIgnore public boolean getHasBounds() { return !empty(bounds); }
    public boolean hasBound(String name) { return !empty(bounds) && Arrays.stream(bounds).anyMatch(b -> b.getName().equals(name)); }

    public SearchQuery setBound(String name, String value) {
        if (bounds == null) bounds = NameAndValue.EMPTY_ARRAY;
        bounds = ArrayUtil.append(bounds, new NameAndValue(name, value));
        return this;
    }

    @Getter @Setter private String[] fields;
    @JsonIgnore public boolean getHasFields () { return !empty(fields); }

    @Getter @Setter private NameAndValue[] fieldMappings;
    @JsonIgnore public boolean hasFieldMappings() { return !empty(fieldMappings); }

    @JsonIgnore @Getter @Setter private SearchScrubber scrubber;
    public boolean hasScrubber () { return scrubber != null; }

    @JsonIgnore @Getter @Setter private String locale;
    public boolean hasLocale () { return locale != null; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SearchQuery that = (SearchQuery) o;

        if (getPageNumber() != that.getPageNumber()) return false;
        if (getPageSize() != that.getPageSize()) return false;
        if (!Arrays.equals(that.bounds, bounds)) return false;
        if (filter != null ? !filter.equals(that.filter) : that.filter != null) return false;
        if (!Arrays.equals(that.sorts, sorts)) return false;
        if (!Arrays.equals(that.fields, fields)) return false;
        return true;
    }

    @Override public int hashCode() {
        int result = getPageNumber();
        result = 31 * result + getPageSize();
        result = 31 * result + (hasSorts() ? Arrays.deepHashCode(this.sorts) : 0);
        result = 31 * result + (filter != null ? filter.hashCode() : 0);
        result = 31 * result + (bounds != null ? Arrays.hashCode(bounds) : 0);
        result = 31 * result + (fields != null ? Arrays.hashCode(fields) : 0);
        return result;
    }

    public String hsqlSortClause(String entityAlias) {
        if (!hasSorts()) return null;
        final StringBuilder b = new StringBuilder();
        for (SearchSort s : sorts) {
            if (b.length() > 0) b.append(", ");
            b.append(s.hasFunc() ? s.getFunc()+"(" : "")
                    .append(entityAlias).append(".").append(s.getSortField())
                    .append(s.hasFunc() ? ")" : "")
                    .append(" ").append(s.getSortOrder().name());
        }
        return b.toString();
    }
}
