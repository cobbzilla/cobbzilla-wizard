package org.cobbzilla.wizard.dao;

import java.util.List;

import static org.cobbzilla.util.json.JsonUtil.toJsonOrErr;

public class DAOUtil {

    public static <E> E uniqueResult(List<E> found) {
        if (found == null || found.size() == 0) return null;
        if (found.size() > 1) throw new NonUniqueResultException(toJsonOrErr(found));
        return found.get(0);
    }

}
