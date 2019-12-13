package org.cobbzilla.wizardtest.resources;

import lombok.Getter;
import org.apache.http.client.HttpClient;
import org.cobbzilla.restex.RestexClientConnectionManager;
import org.cobbzilla.restex.targets.TemplateCaptureTarget;
import org.cobbzilla.wizard.client.ApiClientBase;
import org.cobbzilla.wizard.server.RestServer;
import org.cobbzilla.wizard.server.config.PgRestServerConfiguration;
import org.junit.After;
import org.junit.AfterClass;

public abstract class ApiDocsResourceIT<C extends PgRestServerConfiguration, S extends RestServer<C>>
        extends AbstractResourceIT<C, S> {

    protected static boolean docsEnabled = true;

    protected static TemplateCaptureTarget apiDocs = new TemplateCaptureTarget("target/api-examples");

    @Getter(lazy=true) private final ApiClientBase api = initDocsApi();
    protected ApiClientBase initDocsApi() { return new ApiDocsApiClient(super.getApi()); }

    @After public void commitDocCapture () throws Exception { if (docsEnabled) apiDocs.commit(); }

    @AfterClass public static void finalizeDocCapture () throws Exception { if (docsEnabled) apiDocs.close(); }

    public static final ApiDocsApiRunnerListener apiDocsRunnerListener = initApiDocsApiRunnerListener();

    protected static ApiDocsApiRunnerListener initApiDocsApiRunnerListener() {
        return new ApiDocsApiRunnerListener("api-docs", apiDocs);
    }

    public static class ApiDocsApiClient extends ApiClientBase {

        @Override public HttpClient getHttpClient() {
            if (httpClient == null) httpClient = initHttpClient();
            return httpClient;
        }
        @Override public void setHttpClient(HttpClient httpClient) { this.httpClient = httpClient; }

        private final ApiClientBase api;

        public ApiDocsApiClient(ApiClientBase api) {
            super(api.getBaseUri());
            this.api = api;
        }

        protected HttpClient initHttpClient() {
            return docsEnabled ? new RestexClientConnectionManager(apiDocs).getHttpClient() : api.getHttpClient();
        }

        @Override public String getBaseUri() { return api.getBaseUri(); }
    }
}
