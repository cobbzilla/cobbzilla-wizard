package org.cobbzilla.wizard.dao;

import org.cobbzilla.wizard.auth.AuthenticationException;
import org.cobbzilla.wizard.auth.LoginRequest;
import org.cobbzilla.wizard.model.BasicAccount;

public interface BasicAccountDAO<A extends BasicAccount> extends DAO<A> {

    A findByName(String name);

    A authenticate(LoginRequest login) throws AuthenticationException;

    A findByActivationKey(String key);

    A findByResetPasswordToken(String token);

    void setPassword(A account, String password);
}
