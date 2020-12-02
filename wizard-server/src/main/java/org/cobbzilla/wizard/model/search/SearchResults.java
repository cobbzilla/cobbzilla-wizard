package org.cobbzilla.wizard.model.search;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JavaType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.json.JsonUtil;
import org.cobbzilla.wizard.filters.Scrubbable;
import org.cobbzilla.wizard.filters.ScrubbableField;
import org.cobbzilla.wizard.model.OpenApiSchema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

@NoArgsConstructor @Accessors(chain=true) @OpenApiSchema
public class SearchResults<E> implements Scrubbable {

    public static final ScrubbableField[] SCRUBBABLE_FIELDS = new ScrubbableField[]{
            new ScrubbableField(SearchResults.class, "results.*", List.class)
    };

    public SearchResults(List<E> results, int totalCount) {
        this.results = results;
        this.totalCount = totalCount;
    }

    @Override public ScrubbableField[] fieldsToScrub() { return SCRUBBABLE_FIELDS; }

    private static final Map<Class, JavaType> jsonTypeCache = new ConcurrentHashMap<>();
    public static JavaType jsonType(Class klazz) {
        JavaType type = jsonTypeCache.get(klazz);
        if (type == null) {
            type = JsonUtil.PUBLIC_MAPPER.getTypeFactory().constructParametricType(SearchResults.class, klazz);
            jsonTypeCache.put(klazz, type);
        }
        return type;
    }

    @Getter @Setter private List<E> results = new ArrayList<>();
    @Getter @Setter private Integer totalCount;
    @Getter @Setter private String nextPage;
    @Getter @Setter private String error;

    public String getResultType() { return empty(results) ? null : results.get(0).getClass().getName(); }
    public void setResultType (String val) {} // noop

    @JsonIgnore public int total() {
        if (totalCount == null) die("total is unknown");
        return totalCount;
    }

    @JsonIgnore public int count() { return empty(results) ? 0 : results.size(); }

    @JsonIgnore public boolean hasResults() { return !empty(results); }
    @JsonIgnore public boolean hasTotalCount() { return totalCount != null; }

    public SearchResults(List<E> results) { this.results = results; }

    public E getResult(int i) {
        return (i < 0 || i > results.size()-1) ? null : results.get(i);
    }

    public SearchResults<E> addResult (E result) {
        if (results == null) results = new ArrayList<>();
        results.add(result);
        return this;
    }

    public boolean hasNextPage(SearchQuery searchQuery) {
        return getTotalCount() > searchQuery.getPageNumber() * searchQuery.getPageSize();
    }

}
