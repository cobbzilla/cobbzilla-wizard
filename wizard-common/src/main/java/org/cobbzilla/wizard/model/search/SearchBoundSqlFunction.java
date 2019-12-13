package org.cobbzilla.wizard.model.search;

import java.util.List;

public interface SearchBoundSqlFunction {

    String generateSqlAndAddParams(SearchBound bound, List<Object> params, String value);

    static SearchBoundSqlFunction sqlCompare(String operator, SearchBoundValueFunction valueFunction) {
        return (bound, params, value) -> {
            params.add(valueFunction.paramValue(bound, value));
            return bound.getName() + " " + operator + " ?";
        };
    }

    static SearchBoundSqlFunction sqlInCompare(SearchBoundValueFunction valueFunction) {
        return (bound, params, value) -> {
            final List<String> vals = (List<String>) valueFunction.paramValue(bound, value);
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
        return (bound, params, value) -> {
            final StringBuilder b = new StringBuilder();
            for (int i = 0; i < operators.length; i++) {
                if (b.length() > 0) b.append(") AND (");
                b.append(bound.getName()).append(" ").append(operators[i]).append(" ?");
                params.add(valueFunctions[i].paramValue(bound, value));
            }
            return b.insert(0, "(").append(")").toString();
        };
    }

    static SearchBoundSqlFunction sqlNullCompare(boolean isNull) {
        return (bound, params, value) -> bound.getName() + " IS " + (!isNull ? "NOT " : "") + " NULL";
    }

}
