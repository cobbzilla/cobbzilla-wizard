package org.cobbzilla.wizard.dao;

import org.cobbzilla.util.collection.ArrayUtil;
import org.cobbzilla.util.string.StringUtil;
import org.cobbzilla.wizard.model.NamedIdentityBase;

import java.util.*;

import static org.cobbzilla.util.daemon.ZillaRuntime.sorted;
import static org.cobbzilla.util.daemon.ZillaRuntime.sortedList;
import static org.cobbzilla.util.security.ShaUtil.sha256_hex;

public class NamedIdentityBaseDAO<E extends NamedIdentityBase> extends AbstractCRUDDAO<E> {

    public E findByUuid (String name) { return findByName(name); }

    public E findByName(String name) {
        return cacheLookup("findByName_"+name, o -> findByUniqueField("name", name));
    }

    public List<E> findByNameIn(List<String> names) {
        return cacheLookup("findByNameIn_"+sha256_hex(StringUtil.toString(sorted(names), getNameCacheKeySeparator())),
                o -> findByNames(names));
    }

    public List<E> findByNameIn(String[] names) {
        return cacheLookup("findByNameIn_"+sha256_hex(ArrayUtil.arrayToString(sorted(names), getNameCacheKeySeparator())),
                o -> findByNames(names));
    }

    protected List<E> findByNames(Object names) { // names can be array or Collection, sortedList ensures we get a List back
        final List<E> found = findByFieldIn("name", sortedList(names));
        if (names.getClass().isArray()) names = Arrays.asList(names);
        synchronized (cachedNames) { cachedNames.addAll((Collection) names); }
        return found;
    }

    protected String getNameCacheKeySeparator() { return "\n"; }
    private final Set<String> cachedNames = new HashSet<>();

    @Override public void flushObjectCache(E entity) {
        super.flushObjectCache(entity);
        if (cachedNames.contains(entity.getName())) {
            synchronized (cachedNames) {
                flushObjectCache(); // have to flush everything to be safe. something deleted or otherwise getting flushed was included elsewhere in the cache
                cachedNames.clear();
            }
        }
    }

    @Override public boolean flushObjectCache() {
        synchronized (cachedNames) {
            cachedNames.clear();
            return super.flushObjectCache();
        }
    }

}
