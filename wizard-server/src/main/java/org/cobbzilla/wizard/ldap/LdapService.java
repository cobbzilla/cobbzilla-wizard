package org.cobbzilla.wizard.ldap;

import org.cobbzilla.util.system.CommandResult;
import org.cobbzilla.wizard.model.search.ResultPage;
import org.cobbzilla.wizard.server.config.LdapConfiguration;

public interface LdapService {

    public static final String BOUND_NAME = "name";
    public static final String BOUND_DN = "dn";
    public static final String BOUND_BASE = "base";

    public LdapConfiguration getConfiguration();

    /**
     * Search for a single DN
     * @param userDn the user to authenticate against LDAP with
     * @param password the user's password
     * @param dn the DN to search for
     * @return an LDIF with the results (may or may not contain a match)
     */
    public String ldapsearch(String userDn, String password, String dn);

    /**
     * Search LDAP
     * @param userDn the user to authenticate against LDAP with
     * @param password the user's password
     * @param page the search criteria
     * @return an LDIF with the results (may or may not contain a match)
     */
    public String ldapsearch(String userDn, String password, ResultPage page);

    /**
     * Same as ldapsearch, but authenticates to LDAP as an admin user
     * @param dn the DN to search for
     * @return an LDIF with the results (may or may not contain a match)
     */
    public String rootsearch(String dn);

    /**
     * Same as ldapsearch, but authenticates to LDAP as an admin user
     * @param page the search criteria
     * @return an LDIF with the results (may or may not contain a match)
     */
    public String rootsearch(ResultPage page);

    /**
     * Add an entry to LDAP
     * @param ldif the LDIF to add
     * @return the command result
     */
    public CommandResult ldapadd(String ldif);

    /**
     * Modify an entry in LDAP
     * @param ldif the LDIF with modifications
     * @return the command result
     */
    public CommandResult ldapmodify(String ldif);

    /**
     * Delete an entry from LDAP
     * @param dn the DN to delete
     * @return the command result
     */
    public CommandResult ldapdelete(String dn);

}
