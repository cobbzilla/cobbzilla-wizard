package org.cobbzilla.wizard.dao.entityconfig;

import org.cobbzilla.wizard.dao.DAO;
import org.cobbzilla.wizard.model.ParentAndSuccessor;

import static org.cobbzilla.wizard.resources.ResourceUtil.notFoundEx;

public class ParentAndSuccessorDAO {

    public static <T extends ParentAndSuccessor> T findCurrent(String id, T thing, DAO<T> dao) {
        if (thing == null) throw notFoundEx(id);
        final StringBuilder b = new StringBuilder(id);
        while (thing.hasSuccessor()) {
            thing = dao.findByUuid(thing.getSuccessor());
            if (thing == null) throw notFoundEx(b.toString());
            b.append(" >> ").append(thing.getSuccessor());
        }
        return thing;
    }

}
