package org.cobbzilla.wizard.model.search;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AllArgsConstructor;
import org.cobbzilla.util.collection.ComparisonOperator;
import org.cobbzilla.util.time.TimePeriodType;
import org.cobbzilla.util.time.TimeUtil;

import java.util.Collections;
import java.util.List;

import static org.apache.commons.lang.StringUtils.isAlphanumericSpace;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.string.StringUtil.splitAndTrim;
import static org.cobbzilla.util.string.StringUtil.sqlFilter;
import static org.cobbzilla.wizard.model.search.SearchBoundSqlFunction.*;
import static org.cobbzilla.wizard.model.search.SearchField.OP_SEP;

@AllArgsConstructor
public enum SearchBoundComparison {

    eq      (sqlCompare(ComparisonOperator.eq.sql, SearchBoundComparison::parseCompareArgument)),
    ne      (sqlCompare(ComparisonOperator.ne.sql, SearchBoundComparison::parseCompareArgument)),
    gt      (sqlCompare(ComparisonOperator.gt.sql, SearchBoundComparison::parseCompareArgument)),
    ge      (sqlCompare(ComparisonOperator.ge.sql, SearchBoundComparison::parseCompareArgument)),
    lt      (sqlCompare(ComparisonOperator.lt.sql, SearchBoundComparison::parseCompareArgument)),
    le      (sqlCompare(ComparisonOperator.le.sql, SearchBoundComparison::parseCompareArgument)),

    like    (sqlCompare( "ilike", SearchBoundComparison::parseLikeArgument)),

    is_null (sqlNullCompare(true)),
    not_null(sqlNullCompare(false)),
    empty   (sqlCompare(ComparisonOperator.eq.sql, (b, v, l) -> "")),
    in      (sqlInCompare(SearchBoundComparison::parseInArgument)),

    before  (sqlCompare(ComparisonOperator.le.sql, SearchBoundComparison::parseDateArgument)),
    after   (sqlCompare(ComparisonOperator.ge.sql, SearchBoundComparison::parseDateArgument)),

    during  (sqlAndCompare(new String[] {
            ComparisonOperator.ge.sql,
            ComparisonOperator.le.sql
    }, new SearchBoundValueFunction[] {
            (bound, value, locale) -> TimePeriodType.fromString(value).start(),
            (bound, value, locale) -> TimePeriodType.fromString(value).end()
    })),

    custom  (null);

    private static Object parseInArgument(SearchBound bound, String val, String locale) {
        if (empty(val)) return Collections.emptyList();
        char delim = ',';
        if (!isAlphanumericSpace(""+val.charAt(0))) {
            delim = val.charAt(0);
            val = val.substring(1);
        }
        return splitAndTrim(val, "" + delim);
    }

    private SearchBoundSqlFunction sqlFunction;

    private static Object parseLikeArgument(SearchBound bound, String val, String locale) { return sqlFilter(val); }

    private static Object parseCompareArgument(SearchBound bound, String val, String locale) {
        SearchFieldType type = bound.getType();
        if (type == null) {
            final SearchBoundComparison comparison = bound.getComparison();
            switch (comparison) {
                case eq: case ne: type = SearchFieldType.string;  break;
                default:          type = SearchFieldType.integer; break;
            }
        }
        switch (type) {
            case flag:            return Boolean.parseBoolean(val);
            case integer:         return Long.parseLong(val);
            case decimal:         return Double.parseDouble(val);
            case string: default: return val;
        }
    }

    private static Object parseDateArgument(SearchBound bound, String val, String locale) {
        try {
            Object t = TimeUtil.parseWithLocale(val, locale);
            if (t != null) return t;
        } catch (Exception ignored) {
            // noop
        }
        try {
            return Long.parseLong(val);
        } catch (Exception e) {
            throw SearchField.invalid("err.param.invalid", "parseDateArgument: '"+val+"' is not a valid date or epoch time", val);
        }
    }

    public boolean isCustom() { return this == custom; }

    @JsonCreator public static SearchBoundComparison fromString (String val) { return valueOf(val.toLowerCase()); }

    public static SearchBoundComparison fromStringOrNull (String val) {
        try { return fromString(val); } catch (Exception ignored) { return null; }
    }

    public SearchBound bind(String name) { return bind(name, (SearchFieldType) null); }

    public SearchBound bind(String name, SearchFieldType type) {
        return this == custom
                ? die("bind: cannot bind name to custom comparison: "+name)
                : new SearchBound(name, this, type, null, null);
    }

    public SearchBound bind(String name, SearchFieldType type, String[] params, Class<? extends CustomSearchBoundProcessor> processorClass) {
        return this != custom
                ? die("bind: cannot bind name to non-custom comparison: "+name)
                : new SearchBound(name, custom, type, params, processorClass.getName());
    }

    public SearchBound bind(String name, Class<? extends CustomSearchBoundProcessor> processorClass) {
        return this != custom
                ? die("bind: cannot bind name to non-custom comparison: "+name)
                : new SearchBound(name, custom, SearchFieldType.string, null, processorClass.getName());
    }

    public String sql(SearchBound bound, List<Object> params, String value, String locale) {
        return sqlFunction.generateSqlAndAddParams(bound, params, value, locale);
    }

    public static String customPrefix(String op) { return SearchBoundComparison.custom.name() + OP_SEP + op + OP_SEP; }

}
