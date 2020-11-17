package org.cobbzilla.wizard.main;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.wizard.api.NotFoundException;
import org.cobbzilla.wizard.client.ApiClientBase;
import org.cobbzilla.wizard.util.RestResponse;

import static lombok.AccessLevel.PROTECTED;
import static org.cobbzilla.util.daemon.ZillaRuntime.notSupported;
import static org.cobbzilla.util.io.StreamUtil.readLineFromStdin;
import static org.cobbzilla.util.json.JsonUtil.toJson;

@Slf4j
public abstract class MainApiBase<OPT extends MainApiOptionsBase> extends MainBase<OPT> {

    public static final String TOKEN_PREFIX = "token:";

    @Getter(value=PROTECTED, lazy=true) private final ApiClientBase apiClient = initApiClient();
    protected ApiClientBase initApiClient() { return new ApiTokenClient<>(this); }

    @Override protected void preRun() { if (getOptions().requireAccount()) login(); }

    /** @return the Java object to POST as JSON for the login */
    protected abstract Object buildLoginRequest(OPT options);

    /** @return the name of the HTTP header that will hold the session id on future requests */
    protected abstract String getApiHeaderTokenName();

    /** @return the URI to POST the login request to (account may be null if the URI does not take an account) */
    protected abstract String getLoginUri(String account);

    protected abstract String getSessionId(RestResponse response) throws Exception;

    protected void setSecondFactor(Object loginRequest, String token) { notSupported("setSecondFactor"); }

    protected void login () {
        final OPT options = getOptions();
        final String account = getOptions().getAccount();
        if (account.startsWith(TOKEN_PREFIX)) {
            final String token = account.substring(TOKEN_PREFIX.length());
            log.info("not logging in, using token provided on command line instead");
            getApiClient().pushToken(token);

        } else {
            log.info("logging in " + account + " ...");
            try {
                final Object loginRequest = buildLoginRequest(options);
                final ApiClientBase api = getApiClient();
                final String loginUri = getLoginUri(account);

                RestResponse response = api.post(loginUri, toJson(loginRequest));
                if (response.json.contains("\"2-factor\"")) {
                    final String token = getOptions().hasTwoFactor()
                            ? getOptions().getTwoFactor()
                            : readLineFromStdin("Please enter token for 2-factor authentication: ");
                    setSecondFactor(loginRequest, token);
                    response = getApiClient().post(loginUri, toJson(loginRequest));
                }
                api.pushToken(getSessionId(response));

            } catch (NotFoundException e) {
                handleAccountNotFound(account);

            } catch (Exception e) {
                die("Error logging in: " + e, e);
            }
        }
    }

    protected void handleAccountNotFound(String account) {
        die("Account not found: "+account + " (API was "+getOptions().getApiBase()+")");
    }

    public static class ApiTokenClient<OPT extends MainApiOptionsBase> extends ApiClientBase {
        private String tokenHeader;
        public ApiTokenClient(MainApiBase command) {
            super(((OPT) command.getOptions()).getApiBase());
            this.tokenHeader = command.getApiHeaderTokenName();
        }
        public ApiTokenClient(ApiClientBase client) {
            super(client);
            setToken(client.getToken());
            this.tokenHeader = client.getTokenHeader();
        }
        @Override public String getTokenHeader() { return tokenHeader; }
    }
}
