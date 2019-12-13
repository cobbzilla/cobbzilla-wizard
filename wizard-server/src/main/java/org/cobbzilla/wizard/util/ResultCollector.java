package org.cobbzilla.wizard.util;

import org.cobbzilla.wizard.dao.EntityFilter;

import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

public interface ResultCollector {

    List getResults();

    /* return true if there is room for more things to be added. caller should stop collecting when this returns false */
    boolean addResult(Object thing);

    EntityFilter getEntityFilter();
    ResultCollector setEntityFilter(EntityFilter filter);

    int size();

    int getMaxResults();
    ResultCollector setMaxResults(int max);

    List await(List<Future<List>> futures, long timeout) throws TimeoutException;

}
