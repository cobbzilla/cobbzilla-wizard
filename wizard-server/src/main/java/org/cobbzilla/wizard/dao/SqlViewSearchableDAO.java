package org.cobbzilla.wizard.dao;

import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.search.ResultPage;
import org.cobbzilla.wizard.model.search.SqlViewField;

import java.util.List;

import static org.cobbzilla.util.string.StringUtil.sqlFilter;

public interface SqlViewSearchableDAO<T extends Identifiable> extends DAO<T> {

    String getSearchView();

    String getSelectClause(ResultPage resultPage);

    default String fixedFilters() { return "1=1"; }

    SqlViewField[] getSearchFields();

    default String[] getSearchFieldNames() {
        final SqlViewField[] searchFields = getSearchFields();
        final String[] names = new String[searchFields.length];
        for (int i=0; i<searchFields.length; i++) names[i] = searchFields[i].getName();
        return names;
    }

    default String buildFilter(ResultPage resultPage, List<Object> params) {
        final String filter = sqlFilter(resultPage.getFilter());
        final SqlViewField[] fields = getSearchFields();
        int filterCount = 0;
        final StringBuilder b = new StringBuilder();
        for (SqlViewField f : fields) {
            if (f.isFilter()) {
                filterCount++;
                if (b.length() > 0) b.append(" OR ");
                b.append(f.getName()).append(" ilike ?");
            }
        }
        for (int i=0; i<filterCount; i++) params.add(filter);
        return b.toString();
    }

    String buildBound(String bound, String value, List<Object> params);

    String getSortField(String sortField);

    String getDefaultSort();

}
