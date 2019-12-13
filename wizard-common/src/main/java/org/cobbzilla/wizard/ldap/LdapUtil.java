package org.cobbzilla.wizard.ldap;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;

public class LdapUtil {

    public static final String DN_PREFIX = "dn: ";

    public static String getFirstDnLabel(String dn) {
        // sanity check
        if (dn.startsWith(DN_PREFIX)) dn = dn.substring(DN_PREFIX.length());

        int eqPos = dn.indexOf("=");
        if (eqPos == -1 || eqPos == dn.length()-1) die("getFirstDnLabel: invalid DN: '"+dn+"'");

        return dn.substring(0, eqPos);
    }

    public static String getFirstDnValue(String dn) {
        // sanity check
        if (dn.startsWith(DN_PREFIX)) dn = dn.substring(DN_PREFIX.length());

        int eqPos = dn.indexOf("=");
        if (eqPos == -1 || eqPos == dn.length()-1) die("getFirstDnValue: invalid DN: '"+dn+"'");

        dn = dn.substring(eqPos+1);
        int commaPos = dn.indexOf(",");
        if (commaPos == -1 || commaPos == dn.length()-1) return dn;
        return dn.substring(0, commaPos);
    }

}
