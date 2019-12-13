package org.cobbzilla.wizard.dao;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Transformer;
import org.cobbzilla.util.collection.NameAndValue;
import org.cobbzilla.wizard.ldap.LdapService;
import org.cobbzilla.wizard.model.search.ResultPage;
import org.cobbzilla.wizard.model.ldap.LdapEntity;
import org.cobbzilla.wizard.server.config.LdapConfiguration;

import javax.validation.Valid;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.reflect.ReflectionUtil.getFirstTypeParam;
import static org.cobbzilla.util.reflect.ReflectionUtil.instantiate;
import static org.cobbzilla.wizard.model.ldap.LdapAttributeType.OBJECT_CLASS;

@Slf4j
public abstract class AbstractLdapDAO<E extends LdapEntity> implements DAO<E> {

    @Getter(lazy=true) private final E templateObject = initTemplate();
    private E initTemplate() {
        final LdapEntity template = ((LdapEntity) instantiate(getFirstTypeParam(getClass(), LdapEntity.class))).setLdapContext(config());
        return (E) template;
    }
    public Class<? extends LdapEntity> entityClass() { return getTemplateObject().getClass(); }
    public String entitySimpleName() { return getTemplateObject().getClass().getSimpleName(); }
    public String parentDN() { return getTemplateObject().getParentDn(); }
    public String idField() { return getTemplateObject().getIdField(); }

    protected abstract LdapService getLdapService();

    // convenience methods
    protected LdapService ldap() { return getLdapService(); }
    public LdapConfiguration config() { return getLdapService().getConfiguration(); }

    @Getter private final DnTransformer dnTransformer = new DnTransformer();
    private class DnTransformer implements Transformer {
        @Override public Object transform(Object input) {
            LdapEntity entity = (LdapEntity) input;
            return findByDn(entity.getDn());
        }
    }
    protected List<E> dnTransform (List<E> list) {
        return (List<E>) CollectionUtils.collect(list, dnTransformer);
    }

    protected String getUserDn(String accountName) { return idField() +"="+accountName.toLowerCase()+","+ config().getUser_dn(); }

    public void authenticate(String accountName, String password) {
        ldap().ldapsearch(getUserDn(accountName), password, getUserDn(accountName));
    }

    protected String formatBound(String bound, String value) { return notSupported("Invalid bound: " + bound); }

    @Override public SearchResults<E> search(ResultPage resultPage, String entityAlias) {
        mapBounds(resultPage);
        final String ldif = ldap().rootsearch(resultPage);
        final List<E> matches = multiFromLdif(ldif);
        final SearchResults results = new SearchResults().setTotalCount(matches.size());
        for (int i=resultPage.getPageOffset(); i<matches.size() && i<resultPage.getPageEndOffset(); i++) {
            results.addResult(matches.get(i));
        }
        // the caller may want the results filtered (remove sensitive fields)
        if (resultPage.hasScrubber() && !results.getResults().isEmpty()) {
            results.setResults(resultPage.getScrubber().scrub(results.getResults()));
        }
        return results;
    }

    private NameAndValue[] mapBounds(ResultPage resultPage) {
        if (!resultPage.getHasBounds()) return null;
        final NameAndValue[] bounds = resultPage.getBounds();
        final NameAndValue[] mapped = new NameAndValue[bounds.length];
        for (int i=0; i<bounds.length; i++) {
            mapped[i] = new NameAndValue(getTemplateObject().typeForJava(bounds[i].getName()).getLdapName(), bounds[i].getValue());
        }
        resultPage.setBounds(mapped);
        return mapped;
    }

    public String formatSearchFilter(String filter, Map<String, String> bounds) {
        String query = bounds.containsKey(LdapService.BOUND_NAME) ? "" : "("+idField()+"=*)";
        if (!empty(filter)) {
            query = "(&" + query + "(|"
                    + attrFilter(config().getUser_email(), filter)
                    + attrFilter(config().getUser_displayname(), filter)
                    + attrFilter(config().getUser_firstname(), filter)
                    + attrFilter(config().getUser_lastname(), filter)
                    + attrFilter(config().getUser_username(), filter)
                    + "))";
        }
        if (!empty(bounds)) {
            final String searchBounds = formatSearchBounds(bounds);
            query = empty(query) ? searchBounds : "(&" + query + searchBounds + ")";
        }
        return query;
    }

    private String formatSearchBounds(Map<String, String> bounds) {
        if (empty(bounds)) die("formatSearchBounds: no bounds");
        final StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String> bound : bounds.entrySet()) {
            final String ldapField = getTemplateObject().typeForJava(bound.getKey()).getLdapName();
            b.append(formatBound(ldapField, bound.getValue()));
        }
        return bounds.size() > 1 ? "(&"+b.toString()+")" : b.toString();
    }

    private String attrFilter(String field, String filter) { return "(" + field + "=*" + filter + "*)"; }

    @Override public List<E> findAll() {
        final String ldif = ldap().rootsearch(new ResultPage()
                .setBound(LdapService.BOUND_NAME, "*")
                .setBound(LdapService.BOUND_BASE, parentDN()));
        return multiFromLdif(ldif);
    }

    public List<E> findByField(String field, Object value) {
        final ResultPage page = new ResultPage()
                .setBound(LdapService.BOUND_BASE, parentDN())
                .setBound(field, value.toString());
        if (!field.equals(LdapService.BOUND_NAME)) {
            page.setBound(LdapService.BOUND_NAME, "*");
        }
        final String ldif = ldap().rootsearch(page);
        return multiFromLdif(ldif);
    }

    @Override public E findByUniqueField(String field, Object value) {
        final List<E> found = findByField(field, value);
        switch (found.size()) {
            case 0: return null;
            case 1: return findByDn(found.get(0).getDn());
            default: return die("findByUniqueField: multiple results found with "+field+"="+value);
        }
    }

    public E fromLdif(String ldif) {
        E entity = null;
        for (String line : ldif.split("\n")) {
            line = line.trim();
            if (line.startsWith("#")) continue;
            if (line.length() == 0) {
                if (entity != null) break;
                continue;
            }

            int colonPos = line.indexOf(":");
            if (colonPos == -1 || colonPos == line.length()-1) continue;

            final String name = line.substring(0, colonPos).trim();
            final String value = line.substring(colonPos+1).trim();
            if (name.equals("dn")) {
                if (empty(value) || !value.endsWith(parentDN())) {
                    log.debug("Ignoring DN (" + value + "), not an instance of " + entitySimpleName() + " (expected suffix " + parentDN() + ")");
                    continue;
                }
                if (entity != null) die("multiple results found: " + ldif);
                if (getTemplateObject().isValidDn(value)) {
                    entity = (E) instantiate(entityClass());
                    entity.setLdapContext(config()).setDn(value);
                }

            } else if (entity != null) {
                entity.attrFromLdif(name, value);
            }
        }
        if (entity == null) return null;
        if (!entity.hasAttribute(OBJECT_CLASS)) {
            for (String oc : entity.getObjectClasses()) {
                entity.append(OBJECT_CLASS, oc);
            }
        }
        return (E) entity.validate();
    }

    private List<E> multiFromLdif(String ldif) {
        final String[] ldifs = ldif.split("\ndn: ");
        final List<E> results = new ArrayList<>();
        for (String single : ldifs) {
            if (single.trim().startsWith("#")) continue;
            final E entity = fromLdif("dn: "+single);
            if (entity != null) results.add(entity);
        }
        return results;
    }

    public E findByName (String name) {
        final E found = findByUniqueField("name", name.toLowerCase());
        return found == null ? null : findByDn(found.getDn());
    }

    public E findByDn(String dn) { return fromLdif(ldap().rootsearch(dn)); }

    @Override public E findByUuid(String dn) { return findByDn(dn); }

    @Override public E get(Serializable id) {
        final E found = findByUuid(id.toString());
        return found == null ? null : findByDn(found.getDn());
    }

    @Override public boolean exists(String uuid) { return findByUuid(uuid) != null; }

    @Override public Object preCreate(@Valid E entity) {
        if (!entity.hasUuid()) entity.initUuid();
        return null;
    }

    @Override public E create(@Valid E entity) {
        final Object ctx = preCreate(entity);
        ldap().ldapadd(entity.ldifCreate());
        entity.clean();
        return postCreate(entity, ctx);
    }

    @Override public E createOrUpdate(@Valid E entity) { return entity.hasUuid() ? update(entity) : create(entity); }

    @Override public E postCreate(E entity, Object context) { return entity; }

    @Override public Object preUpdate(@Valid E entity) { return null; }

    @Override public E update(@Valid E entity) {
        final Object ctx = preUpdate(entity);
        final String ldif = entity.ldifModify();
        if (ldif != null) {
            ldap().ldapmodify(ldif);
            entity.clean();
        }
        return postUpdate(entity, ctx);
    }

    @Override public E postUpdate(@Valid E entity, Object context) { return entity; }

    @Override public void delete(String dn) { ldap().ldapdelete(dn); }

}
