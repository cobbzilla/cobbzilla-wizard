package org.cobbzilla.wizard.server.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.cobbzilla.util.http.URIUtil;
import org.cobbzilla.util.system.CommandShell;
import org.cobbzilla.wizard.model.ldap.LdapContext;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.json.JsonUtil.fromJsonOrDie;

@Slf4j
public class LdapConfiguration implements LdapContext {

    public static final String ENT_DOUBLE_QUOTE = "&quot;";
    public static final String ENT_SINGLE_QUOTE = "&#39;";

    @Getter private Map<String, String> config = new HashMap<>();

    @Getter @Setter private String password;

    public void setJson (String json) {
        // yaml escapes some characters with HTML entities. Can't figure out how to tell it not to, so we un-escape them here.
        // Please don't use &quot; or &#39; within any LDAP config values :)
        if (json.contains(ENT_DOUBLE_QUOTE)) json = json.replace(ENT_DOUBLE_QUOTE, "\"");
        if (json.startsWith(ENT_SINGLE_QUOTE)) json = json.substring(ENT_SINGLE_QUOTE.length());
        if (json.endsWith(ENT_SINGLE_QUOTE)) json = json.substring(0, json.length() - ENT_SINGLE_QUOTE.length());
        if (json.contains(ENT_SINGLE_QUOTE)) json = json.replace(ENT_SINGLE_QUOTE, "'");

        final JsonNode node = fromJsonOrDie(json, JsonNode.class);
        if (!(node instanceof ObjectNode)) die("Not a JSON object node: "+json);

        final ObjectNode obj = (ObjectNode) node;
        final Iterator<String> fieldNames = obj.fieldNames();
        while (fieldNames.hasNext()) {
            final String name = fieldNames.next();
            config.put(name, obj.get(name).asText());
        }
    }

    private String val (String field, String defaultValue) {
        final String value = config.get(field);
        return !empty(value) ? value : defaultValue;
    }

    public String domainify (String value) { return "dc="+value.replace(".", ",dc="); }

    public String getServer() { return val("server", "ldap://127.0.0.1:389"); }

    public String getScheme () { return URIUtil.getScheme(getServer()); }
    public String getHost () { return URIUtil.getHost(getServer()); }
    public int getPort () { return URIUtil.getPort(getServer()); }

    public static final String[] TRANSPORTS = {"plain", "tls", "ssl"};
    public static final String[] SECURE_TRANSPORTS = {"tls", "ssl"};

    public String getTransport() { return val("transport", "plain"); }
    public boolean isSecure() { return ArrayUtils.indexOf(SECURE_TRANSPORTS, getTransport()) >= 0; }

    public String getVersion() { return val("version", "3"); }
    public String getDomain () { return val("domain", CommandShell.hostname()); }
    public String getLdap_domain() { return val("ldap_domain", domainify(getDomain())); }
    public String getRealm() { return val("realm", getDomain()); }
    public String getBase_dn() { return val("base_dn", getLdap_domain()); }

    public String getAdmin() { return val("admin", "Directory Manager"); }
    public String getAdmin_dn () { return val("admin_dn", "cn="+getAdmin()); }

    public String getOrg_unit_class () { return val("org_unit_class", "organizationalUnit"); }
    public String getExternal_id() { return val("external_id", "entryUUID"); }

    public String getUsers() { return val("users", "People"); }
    public String getUser_simple_dn () { return "ou="+getUsers(); }
    public String getUser_dn () { return val("user_dn", getUser_simple_dn()+","+getBase_dn()); }
    public String getUser_class() { return val("user_class", "inetorgperson"); }
    public String getUser_filter() { return val("user_filter", "(objectclass="+getUser_class()+")"); }
    public String getUser_username() { return val("user_username", "uid"); }
    public String getUser_email() { return val("user_email", "mail"); }
    public String getUser_displayname() { return val("user_displayname", "displayName"); }
    public String getUser_firstname() { return val("user_firstname", "givenName"); }
    public String getUser_lastname() { return val("user_lastname", "sn"); }
    public String getUser_groupnames() { return val("user_groupnames", "memberOf"); }
    public String getUser_username_rdn() { return val("user_username_rdn", "cn"); }
    public String getUser_password() { return val("user_password", "userPassword"); }
    public String getUser_encryption() { return val("user_encryption", "sha"); }

    public String getGroups() { return val("groups", "Groups"); }
    public String getGroup_simple_dn() { return "ou="+getGroups(); }
    public String getGroup_dn() { return val("group_dn", getGroup_simple_dn()+","+getBase_dn()); }
    public String getGroup_class() { return val("group_class", "groupOfUniqueNames"); }
    public String getGroup_filter() { return val("group_filter", "(objectclass="+getGroup_class()+")"); }
    public String getGroup_name() { return val("group_name", "cn"); }
    public String getGroup_description() { return val("group_description", "description"); }
    public String getGroup_usernames() { return val("group_usernames", "uniqueMember"); }

    @Override public String userDN (String name) { return getUser_username() + "=" + name + "," + getUser_dn(); }
    @Override public String groupDN (String name) { return getGroup_name() + "="  + name + "," + getGroup_dn(); }

    @Override public String getUser_mobilePhone() { return "mobilePhone"; }
    @Override public String getUser_mobilePhoneCountryCode() { return "mobilePhoneCountryCode"; }
    @Override public String getUser_admin() { return "admin"; }
    @Override public String getUser_suspended() { return "suspended"; }
    @Override public String getUser_twoFactor() { return "twoFactor"; }
    @Override public String getUser_twoFactorAuthId() { return "twoFactorAuthId"; }
    @Override public String getUser_lastLogin() { return "lastLogin"; }
    @Override public String getUser_locale() { return "preferredLanguage"; }
    @Override public String getUser_storageQuota() { return "storageQuota"; }

    public String filterGroup(String groupName) {
        String groupFilter = getGroup_filter();
        if (!groupFilter.startsWith("(")) groupFilter = "(" + groupFilter;
        if (!groupFilter.endsWith(")")) groupFilter += ")";
        return empty(groupName) ? groupFilter : "(&"+ groupFilter +"("+ getGroup_name()+"="+groupName+"))";
    }
}
