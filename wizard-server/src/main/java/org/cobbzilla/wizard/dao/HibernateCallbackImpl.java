package org.cobbzilla.wizard.dao;

import org.hibernate.HibernateException;
import org.hibernate.Query;
import org.hibernate.Session;
import org.springframework.orm.hibernate4.HibernateCallback;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HibernateCallbackImpl<T> implements HibernateCallback<List<T>> {

    private String queryString;
    private String[] paramNames;
    private Object[] values;

    private Set<String> paramNamesThatAreLists;

    public void markAsListParameter(String paramName) {
        if (paramNamesThatAreLists == null) paramNamesThatAreLists = new HashSet<>();
        paramNamesThatAreLists.add(paramName);
    }

    private int firstResult;
    private int maxResults;

    /**
     * Fetches a {@link List} of entities from the database using pagination.
     * Execute HQL query, binding a number of values to ":" named parameters in the query string.
     *
     * @param queryString a query expressed in Hibernate's query language
     * @param paramNames the names of the parameters
     * @param values the values of the parameters
     * @param firstResult a row number, numbered from 0
     * @param maxResults the maximum number of rows
     */
    public HibernateCallbackImpl(
            String queryString,
            String[] paramNames,
            Object[] values,
            int firstResult,
            int maxResults) {
        this.queryString = queryString;
        this.paramNames = paramNames;
        this.values = values;

        this.firstResult = firstResult;
        this.maxResults = maxResults;
    }

    @Override public List<T> doInHibernate(Session session) throws HibernateException {

        boolean isSql = false;
        if (queryString.startsWith(DAO.SQL_QUERY)) {
            isSql = true;
            queryString = queryString.substring(DAO.SQL_QUERY.length());
        }

        final Query query = isSql ? session.createSQLQuery(queryString) : session.createQuery(queryString);
        query.setFirstResult(firstResult);
        query.setMaxResults(maxResults);

        // TODO: throw proper exception when paramNames.length != values.length

        for (int c=0; c<paramNames.length; c++) {
            if (paramNamesThatAreLists != null && paramNamesThatAreLists.contains(paramNames[c])) {
                query.setParameterList(paramNames[c], (List) values[c]);
            } else {
                query.setParameter(paramNames[c], values[c]);
            }
        }

        final List<T> result = query.list();

        return result;
    }

}