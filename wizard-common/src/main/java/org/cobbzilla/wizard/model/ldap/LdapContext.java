package org.cobbzilla.wizard.model.ldap;

public interface LdapContext {

    public String userDN (String name);
    public String groupDN (String name);

    public String getExternal_id();

    public String getUser_dn();
    public String getUser_username_rdn();
    public String getUser_displayname();
    public String getUser_password();
    public String getUser_username();
    public String getUser_email();
    public String getUser_firstname();
    public String getUser_lastname();
    public String getUser_mobilePhone();
    public String getUser_mobilePhoneCountryCode();
    public String getUser_admin();
    public String getUser_suspended();
    public String getUser_twoFactor();
    public String getUser_twoFactorAuthId();
    public String getUser_lastLogin();
    public String getUser_locale();
    public String getUser_storageQuota();

    public String getGroup_dn();
    public String getGroup_name();
    public String getGroup_description();
    public String getGroup_usernames();

}
