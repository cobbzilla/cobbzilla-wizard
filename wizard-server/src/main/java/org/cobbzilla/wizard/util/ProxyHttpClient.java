package org.cobbzilla.wizard.util;

import lombok.Getter;
import lombok.Setter;
import org.apache.http.HttpResponse;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.*;
import org.cobbzilla.util.http.CookieJar;
import org.cobbzilla.util.http.HttpRequestBean;

import java.io.Closeable;
import java.io.IOException;

public class ProxyHttpClient implements Closeable {

    @Getter @Setter private CloseableHttpClient httpClient;
    @Getter @Setter private CredentialsProvider credsProvider = null;
    @Getter @Setter HttpClientContext localContext = null;
    @Getter @Setter CookieJar cookieJar = null;

    public HttpResponse execute(HttpUriRequest request) throws IOException { return httpClient.execute(request, localContext); }

    @Override public void close() throws IOException { if (httpClient != null) httpClient.close(); }

    public ProxyHttpClient(HttpRequestBean requestBean) {
        this(requestBean, null);
    }

    public ProxyHttpClient(HttpRequestBean requestBean, CookieJar cookieJar) {
        if (cookieJar == null) cookieJar = new CookieJar();
        this.cookieJar = cookieJar;
        final HttpClientBuilder clientBuilder = requestBean.initClientBuilder(HttpClients.custom().setDefaultCookieStore(cookieJar));
        httpClient = clientBuilder.build();
    }

}
