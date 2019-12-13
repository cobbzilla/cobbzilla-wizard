package org.cobbzilla.wizard.ldap;

import org.apache.commons.exec.CommandLine;
import org.cobbzilla.util.collection.NameAndValue;
import org.cobbzilla.util.system.Command;
import org.cobbzilla.util.system.CommandResult;
import org.cobbzilla.util.system.CommandShell;
import org.cobbzilla.wizard.model.ldap.LdapBindException;
import org.cobbzilla.wizard.model.search.ResultPage;
import org.cobbzilla.wizard.server.config.LdapConfiguration;

import java.util.Map;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.system.CommandShell.okResult;

public abstract class LdapServiceBase implements LdapService {

    private LdapConfiguration config() { return getConfiguration(); }

    private String password() { return config().getPassword(); }
    public String accountDN(String accountName) { return config().userDN(accountName); }
    public String groupDN  (String groupName)   { return config().groupDN(groupName); }
    public String adminDN  ()                   { return config().getAdmin_dn(); }

    @Override public CommandResult ldapadd(String ldif) { return okResult(exec(ldapRootCommand("ldapadd"), ldif)); }

    @Override public CommandResult ldapmodify(String ldif) { return ldapmodify(ldif, true); }

    private CommandResult ldapmodify(String ldif, boolean checkOk) {
        final CommandResult result = exec(ldapRootCommand("ldapmodify"), ldif);
        return checkOk ? okResult(result) : result;
    }

    @Override public CommandResult ldapdelete(String dn) {
        return okResult(exec(ldapRootCommand("ldapdelete").addArgument(dn)));
    }

    private ResultPage resultPage(String dn) { return new ResultPage().setBound(BOUND_DN, dn); }

    @Override public String rootsearch(String dn) { return rootsearch(resultPage(dn)); }
    @Override public String rootsearch(ResultPage page) { return ldapsearch(adminDN(), password(), page); }

    protected abstract String ldapFilter(String base, String filter, Map<String, String> bounds);
    protected abstract String ldapField(String base, String javaName);

    @Override public String ldapsearch(String userDn, String password, String dn) {
        return ldapsearch(userDn, password, resultPage(dn));
    }

    @Override public String ldapsearch(String userDn, String password, ResultPage page) {

        final CommandLine command = ldapCommand("ldapsearch", userDn, password);
        final Map<String, String> bounds = NameAndValue.toMap(page.getBounds());
        final String dn = bounds == null ? null : bounds.remove(BOUND_DN);
        final String base = bounds == null ? null : bounds.remove(BOUND_BASE);
        final String filter = page.getFilter();
        if (base != null) command.addArgument("-b").addArgument(base);
        if (!empty(dn)) {
            if (!empty(bounds)) die("ldapsearch: if bound '"+BOUND_DN+"' is set, no other bounds may be set");
            command.addArgument("-b").addArgument(dn, false);
        } else {
            if (!empty(filter) || !empty(bounds)) command.addArgument(ldapFilter(base, filter, bounds));
            if (page.getHasSortField()) {
                final ResultPage.SortOrder sortOrder = page.getSortType();
                final String sort = page.getSortField();
                if (sort != null) {
                    final String sortArg = ((sortOrder != null && sortOrder == ResultPage.SortOrder.DESC) ? "-" : "");
                    command.addArgument("-E").addArgument("!sss=" + sortArg + sort);
                }
            }
        }
        final CommandResult result = exec(command);
        if (!result.isZeroExitStatus()) {
            final String stderr = result.getStderr();
            if (stderr.contains("ldap_bind: No such object") ||
                stderr.contains("ldap_bind: Invalid credentials")) {
                throw new LdapBindException(userDn);
            } else {
                die("ldap_search: "+result);
            }
        }
        return result.getStdout();
    }

    private CommandLine ldapRootCommand(String cmd) { return ldapCommand(cmd, adminDN(), password()); }

    public CommandLine ldapCommand(String command, String dn, String password) {
        return new CommandLine(command)
                .addArgument("-x")
                .addArgument("-H")
                .addArgument(config().getServer())
                .addArgument("-D")
                .addArgument(dn, false)
                .addArgument("-w")
                .addArgument(password);
    }

    private CommandResult exec(CommandLine command) { return exec(command, null); }

    private CommandResult exec(CommandLine command, String input) {
        final CommandResult result;
        final Command cmd = empty(input) ? new Command(command) : new Command(command).setInput(input);
        try {
            result = CommandShell.exec(cmd);
        } catch (Exception e) {
            return die("error running "+command+": " + e,e);
        }
        return result;
    }

    public void changePassword(String accountName, String oldPassword, String newPassword) {
        final CommandLine command =  ldapRootCommand("ldappasswd")
                .addArgument("-a").addArgument(oldPassword)
                .addArgument("-s").addArgument(newPassword)
                .addArgument(accountDN(accountName));
        okResult(exec(command));
    }

    public void adminChangePassword(String accountName, String newPassword) {
        final CommandLine command =  ldapRootCommand("ldappasswd")
                .addArgument("-s").addArgument(newPassword)
                .addArgument(accountDN(accountName));
        okResult(exec(command));
    }
}
