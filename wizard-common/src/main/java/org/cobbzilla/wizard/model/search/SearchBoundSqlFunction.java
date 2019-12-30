package org.cobbzilla.wizard.model.search;

import java.util.List;

public interface SearchBoundSqlFunction {

    String generateSqlAndAddParams(SearchBound bound, List<Object> params, String value, String locale);

    static SearchBoundSqlFunction sqlCompare(String operator, SearchBoundValueFunction valueFunction) {
        return (bound, params, value, locale) -> {
            params.add(valueFunction.paramValue(bound, value, locale));
            return bound.getName() + " " + operator + " ?";
        };
    }

    static SearchBoundSqlFunction sqlInCompare(SearchBoundValueFunction valueFunction) {
        return (bound, params, value, locale) -> {
            final List<String> vals = (List<String>) valueFunction.paramValue(bound, value, locale);
            final StringBuilder sql = new StringBuilder();
            for (String val : vals) {
                params.add(val);
                if (sql.length() > 0) sql.append(" OR ( ");
                sql.append(bound.getName()).append(" = ? )");
            }
            return "( "+sql.toString();
        };
    }

    static SearchBoundSqlFunction sqlAndCompare(String[] operators, SearchBoundValueFunction[] valueFunctions) {
        return (bound, params, value, locale) -> {
            final StringBuilder b = new StringBuilder();
            for (int i = 0; i < operators.length; i++) {
                if (b.length() > 0) b.append(") AND (");
                b.append(bound.getName()).append(" ").append(operators[i]).append(" ?");
                params.add(valueFunctions[i].paramValue(bound, value, locale));
            }
            return b.insert(0, "(").append(")").toString();
        };
    }

    static SearchBoundSqlFunction sqlNullCompare(boolean isNull) {
        return (bound, params, value, locale) -> bound.getName() + " IS " + (!isNull ? "NOT " : "") + " NULL";
    }

}
