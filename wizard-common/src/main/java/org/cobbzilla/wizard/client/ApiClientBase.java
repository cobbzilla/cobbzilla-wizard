package org.cobbzilla.wizard.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.map.SingletonMap;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpContext;
import org.cobbzilla.util.collection.NameAndValue;
import org.cobbzilla.util.http.ApiConnectionInfo;
import org.cobbzilla.util.http.HttpRequestBean;
import org.cobbzilla.util.http.HttpResponseBean;
import org.cobbzilla.util.http.HttpUtil;
import org.cobbzilla.util.reflect.ReflectionUtil;
import org.cobbzilla.wizard.api.ApiException;
import org.cobbzilla.wizard.api.ForbiddenException;
import org.cobbzilla.wizard.api.NotFoundException;
import org.cobbzilla.wizard.api.ValidationException;
import org.cobbzilla.wizard.model.entityconfig.ModelEntity;
import org.cobbzilla.wizard.util.RestResponse;

import java.io.*;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;
import static org.apache.http.HttpHeaders.LOCATION;
import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.http.HttpContentTypes.APPLICATION_OCTET_STREAM;
import static org.cobbzilla.util.http.HttpMethods.*;
import static org.cobbzilla.util.http.HttpSchemes.isHttpOrHttps;
import static org.cobbzilla.util.http.HttpStatusCodes.*;
import static org.cobbzilla.util.io.FileUtil.getDefaultTempDir;
import static org.cobbzilla.util.json.JsonUtil.fromJson;
import static org.cobbzilla.util.json.JsonUtil.toJson;
import static org.cobbzilla.util.reflect.ReflectionUtil.closeQuietly;
import static org.cobbzilla.util.reflect.ReflectionUtil.instantiate;
import static org.cobbzilla.util.string.StringUtil.UTF8cs;
import static org.cobbzilla.util.system.Sleep.sleep;
import static org.cobbzilla.util.time.TimeUtil.formatDuration;
import static org.cobbzilla.wizard.model.Identifiable.ENTITY_TYPE_HEADER_NAME;

@Slf4j @NoArgsConstructor @ToString(of={"connectionInfo"})
public class ApiClientBase implements Cloneable, Closeable {

    public static final ContentType CONTENT_TYPE_JSON = ContentType.APPLICATION_JSON;
    public static final long INITIAL_RETRY_DELAY = TimeUnit.SECONDS.toMillis(1);

    @SuppressWarnings("CloneDoesntCallSuperClone") // subclasses must have a copy constructor
    @Override public Object clone() { return instantiate(getClass(), this); }

    @Getter @Setter protected ApiConnectionInfo connectionInfo;
    @Getter protected String token;

    public String getSuperuserToken () { return null; } // subclasses may override

    @Getter @Setter protected String entityTypeHeaderName = ENTITY_TYPE_HEADER_NAME;
    public boolean hasEntityTypeHeaderName () { return !empty(entityTypeHeaderName); }

    // the server may be coming up, and either not accepting connections or issuing 503 Service Unavailable.
    @Getter @Setter protected int numTries = 5;
    @Getter @Setter protected long retryDelay = INITIAL_RETRY_DELAY;

    @Getter @Setter protected boolean captureHeaders = false;
    @Getter @Setter private HttpContext httpContext = null;
    @Getter private Map<String, String> headers = null;

    public void setHeaders(JsonNode jsonNode) {
        final ObjectMapper mapper = new ObjectMapper();
        headers = mapper.convertValue(jsonNode, Map.class);
    }
    public void removeHeaders () { headers = null; }

    public ApiClientBase setToken(String token) {
        this.token = token;
        this.tokenCtime = empty(token) ? 0 : now();
        return this;
    }

    private long tokenCtime = 0;
    public boolean hasToken () { return !empty(token); }
    public long getTokenAge () { return now() - tokenCtime; }

    public static final int CONNECT_TIMEOUT = (int) SECONDS.toMillis(10);
    public static final int SOCKET_TIMEOUT = (int) SECONDS.toMillis(60);
    public static final int REQUEST_TIMEOUT = (int) SECONDS.toMillis(60);

    @Getter @Setter private int connectTimeout = CONNECT_TIMEOUT;
    @Getter @Setter private int socketTimeout = SOCKET_TIMEOUT;
    @Getter @Setter private int requestTimeout = REQUEST_TIMEOUT;

    public void logout () { setToken(null); }

    public ApiClientBase (ApiClientBase other) { this.connectionInfo = other.getConnectionInfo(); }
    public ApiClientBase (ApiConnectionInfo connectionInfo) { this.connectionInfo = connectionInfo; }
    public ApiClientBase (String baseUri) { connectionInfo = new ApiConnectionInfo(baseUri); }

    public ApiClientBase (ApiConnectionInfo connectionInfo, HttpClient httpClient) {
        this(connectionInfo);
        setHttpClient(httpClient);
    }

    public ApiClientBase (String baseUri, HttpClient httpClient) {
        this(baseUri);
        setHttpClient(httpClient);
    }

    public String getBaseUri () { return connectionInfo.getBaseUri(); }

    protected HttpClient httpClient;
    public HttpClient getHttpClient() {
        if (httpClient == null) {
            final RequestConfig.Builder requestBuilder = RequestConfig.custom()
                    .setConnectTimeout(connectTimeout)
                    .setSocketTimeout(socketTimeout)
                    .setConnectionRequestTimeout(requestTimeout);

            httpClient = getHttpClientBuilder()
                    .setDefaultRequestConfig(requestBuilder.build())
                    .build();
        }
        return httpClient;
    }

    public HttpClientBuilder getHttpClientBuilder() { return HttpClientBuilder.create(); }

    public void setHttpClient(HttpClient httpClient) { this.httpClient = httpClient; }

    public RestResponse process(HttpRequestBean requestBean) throws Exception {
        switch (requestBean.getMethod()) {
            case GET:
                return doGet(requestBean.getUri());
            case POST:
                return doPost(requestBean.getUri(), getJson(requestBean));
            case PUT:
                return doPut(requestBean.getUri(), getJson(requestBean));
            case DELETE:
                return doDelete(requestBean.getUri());
            default:
                return die("Unsupported request method: "+requestBean.getMethod());
        }
    }

    public RestResponse process_raw(HttpRequestBean requestBean) throws Exception {
        switch (requestBean.getMethod()) {
            case GET:
                return doGet(requestBean.getUri());
            case POST:
                return doPost(requestBean.getUri(), requestBean.getEntity(), requestBean.getContentType());
            case PUT:
                return doPut(requestBean.getUri(), requestBean.getEntity(), requestBean.getContentType());
            case DELETE:
                return doDelete(requestBean.getUri());
            default:
                return die("Unsupported request method: "+requestBean.getMethod());
        }
    }

    protected void assertStatusOK(RestResponse response) {
        if (response.status != OK
                && response.status != CREATED
                && response.status != NO_CONTENT) throw new ApiException(response);
    }

    protected String getJson(HttpRequestBean requestBean) throws Exception {
        Object data = requestBean.getEntity();
        if (data == null) return null;
        if (data instanceof String) return (String) data;
        return toJson(data);
    }

    protected ApiException specializeApiException(ApiException e) { return specializeApiException(null, e.getResponse()); }

    protected ApiException specializeApiException(HttpRequestBean request, RestResponse response) {
        if (response.isSuccess()) {
            die("specializeApiException: cannot specialize exception for a successful response: "+response);
        }
        switch (response.status) {
            case NOT_FOUND:
                return new NotFoundException(request, response);
            case FORBIDDEN:
                return new ForbiddenException(request, response);
            case UNPROCESSABLE_ENTITY:
                return new ValidationException(request, response);
            default: return new ApiException(request, response);
        }
    }

    public RestResponse doGet(String path) throws Exception {
        final HttpClient client = getHttpClient();
        final String url = getUrl(path, getBaseUri());
        @Cleanup("releaseConnection") HttpGet httpGet = new HttpGet(url);
        return getResponse(client, httpGet);
    }

    public RestResponse get(String path) throws Exception {
        final RestResponse restResponse = doGet(path);
        if (!restResponse.isSuccess()) throw specializeApiException(HttpRequestBean.get(path), restResponse);
        return restResponse;
    }

    public <T> T get(String path, Class<T> responseClass) throws Exception {
        return fromJson(get(path).json, responseClass);
    }

    protected <T> void setRequestEntity(HttpEntityEnclosingRequest entityRequest, T data, ContentType contentType) {
        if (data != null) {
            if (data instanceof InputStream) {
                entityRequest.setEntity(new InputStreamEntity((InputStream) data, contentType));
                log.debug("setting entity=(InputStream)");
            } else {
                entityRequest.setEntity(new StringEntity(data.toString(), contentType));
                log.debug("setting entity=(" + data.toString().length()+" json chars)");
                log.trace(data.toString());
            }
        }
    }

    public RestResponse doPost(String path, String json) throws Exception {
        return doPost(path, json, CONTENT_TYPE_JSON);
    }

    public <T> RestResponse doPost(String path, T data, ContentType contentType) throws Exception {
        final HttpClient client = getHttpClient();
        final String url = getUrl(path, getBaseUri());
        @Cleanup("releaseConnection") HttpPost httpPost = new HttpPost(url);
        setRequestEntity(httpPost, data, contentType);
        return getResponse(client, httpPost);
    }

    public <T> T post(String path, Object request, Class<T> responseClass) throws Exception {
        if (request instanceof String) return post(path, request, responseClass);
        if (request instanceof ModelEntity) return post(path, ((ModelEntity) request).getEntity(), responseClass);
        return fromJson(post(path, toJson(request)).json, responseClass);
    }

    public RestResponse doPost(String path, File uploadFile) throws Exception {
        return uploadFile(path, uploadFile, POST);
    }

    public RestResponse doPut(String path, File uploadFile) throws Exception {
        return uploadFile(path, uploadFile, PUT);
    }

    private RestResponse uploadFile(String path, File uploadFile, String method) throws Exception {
        final String url = getUrl(path, getBaseUri());
        final NameAndValue[] headers = { new NameAndValue(getTokenHeader(), token) };

        @Cleanup final InputStream in = new FileInputStream(uploadFile);
        final HttpRequestBean request = new HttpRequestBean(method, url, in, uploadFile.getName(), headers);
        final HttpResponseBean response = HttpUtil.getStreamResponse(request);

        return new RestResponse(response);
    }

    public <T> T post(String path, T request) throws Exception {
        if (request instanceof ModelEntity) {
            return (T) post(path, ((ModelEntity) request).getEntity(), ((ModelEntity) request).getEntity().getClass());
        } else {
            return post(path, request, (Class<T>) request.getClass());
        }
    }

    public RestResponse post(String path, String json) throws Exception {
        return post(path, json, CONTENT_TYPE_JSON);
    }

    public RestResponse post(String path, String data, ContentType contentType) throws Exception {
        final RestResponse restResponse = doPost(path, data, contentType);
        if (!restResponse.isSuccess()) throw specializeApiException(HttpRequestBean.post(path, data), restResponse);
        return restResponse;
    }

    public RestResponse put(String path, String data, ContentType contentType) throws Exception {
        final RestResponse restResponse = doPut(path, data, contentType);
        if (!restResponse.isSuccess()) throw specializeApiException(HttpRequestBean.put(path, data), restResponse);
        return restResponse;
    }

    public RestResponse doPut(String path, String json) throws Exception {
        return doPut(path, json, CONTENT_TYPE_JSON);
    }

    public <T> RestResponse doPut(String path, T data, ContentType contentType) throws Exception {
        HttpClient client = getHttpClient();
        final String url = getUrl(path, getBaseUri());
        @Cleanup("releaseConnection") HttpPut httpPut = new HttpPut(url);
        setRequestEntity(httpPut, data, contentType);
        return getResponse(client, httpPut);
    }

    public <T> T put(String path, Object request, Class<T> responseClass) throws Exception {
        return fromJson(put(path, toJson(request)).json, responseClass);
    }

    public <T> T put(String path, T request) throws Exception {
        if (request instanceof ModelEntity) {
            return (T) put(path, ((ModelEntity) request).getEntity(), ((ModelEntity) request).getEntity().getClass());
        } else {
            return put(path, request, (Class<T>) request.getClass());
        }
    }

    public <T> T put(String path, String json, Class<T> responseClass) throws Exception {
        final RestResponse response = put(path, json);
        if (!response.isSuccess()) throw specializeApiException(HttpRequestBean.put(path, json), response);
        return fromJson(response.json, responseClass);
    }

    public RestResponse put(String path, String json) throws Exception {
        final RestResponse restResponse = doPut(path, json);
        if (!restResponse.isSuccess()) throw specializeApiException(HttpRequestBean.put(path, json), restResponse);
        return restResponse;
    }

    public RestResponse doDelete(String path) throws Exception {
        HttpClient client = getHttpClient();
        final String url = getUrl(path, getBaseUri());
        @Cleanup("releaseConnection") HttpDelete httpDelete = new HttpDelete(url);
        return getResponse(client, httpDelete);
    }

    public RestResponse delete(String path) throws Exception {
        final RestResponse restResponse = doDelete(path);
        if (!restResponse.isSuccess()) throw specializeApiException(HttpRequestBean.delete(path), restResponse);
        return restResponse;
    }

    private String getUrl(String path, String clientUri) {
        if (isHttpOrHttps(path)) {
            return path; // caller has supplied an absolute path

        } else if (path.startsWith("/") && clientUri.endsWith("/")) {
            path = path.substring(1); // caller has supplied a relative path
        }
        return clientUri + path;
    }

    public RestResponse getResponse(HttpClient client, HttpRequestBase request) throws IOException {
        request = beforeSend(request);
        RestResponse restResponse = null;
        IOException exception = null;
        retryDelay = INITIAL_RETRY_DELAY;
        for (int i=0; i<numTries; i++) {
            if (i > 0) {
                sleep(retryDelay);
                retryDelay *= 2;
            }
            try {
                final HttpResponse response = execute(client, request);
                final int statusCode = response.getStatusLine().getStatusCode();
                String responseJson = null;
                byte[] responseBytes = null;
                final HttpEntity entity = response.getEntity();
                if (entity != null) {
                    try (InputStream in = entity.getContent()) {
                        if (isCaptureHeaders() && response.containsHeader("content-disposition")) {
                            responseBytes = IOUtils.toByteArray(in);
                        } else {
                            responseJson = IOUtils.toString(in, UTF8cs);
                            log.debug("response: " + responseJson);
                        }
                    }
                } else {
                    responseJson = null;
                }

                restResponse = empty(responseBytes)
                        ? new RestResponse(statusCode, responseJson, getLocationHeader(response))
                        : new RestResponse(statusCode, responseBytes, getLocationHeader(response));
                if (isCaptureHeaders() || hasEntityTypeHeaderName()) {
                    for (Header header : response.getAllHeaders()) {
                        if (isCaptureHeaders() || header.getName().equals(getEntityTypeHeaderName())) {
                            restResponse.addHeader(header.getName(), header.getValue());
                        }
                    }
                }
                if (statusCode != SERVER_UNAVAILABLE) return restResponse;
                log.warn("getResponse("+request.getMethod()+" "+request.getURI().toASCIIString()+", attempt="+i+"/"+numTries+") returned "+SERVER_UNAVAILABLE+", will " + ((i+1)>=numTries ? "NOT":"sleep for "+formatDuration(retryDelay)+" then") + " retry the request");

            } catch (IOException e) {
                log.warn("getResponse("+request.getMethod()+" "+request.getURI().toASCIIString()+", attempt="+i+"/"+numTries+") threw exception "+e+", will " + ((i+1)>=numTries ? "NOT":"sleep for "+formatDuration(retryDelay)+" then") + " retry the request");
                exception = e;
            }
        }
        if (restResponse != null) return restResponse;
        if (exception == null) return die("getResponse: unknown error");
        throw exception;
    }

    public HttpResponse execute(HttpClient client, HttpRequestBase request) throws IOException {
        return client.execute(request, httpContext);
    }

    public File getFile (String path) throws IOException {

        final HttpClient client = getHttpClient();
        final String url = getUrl(path, getBaseUri());
        @Cleanup("releaseConnection") HttpRequestBase request = new HttpGet(url);
        request = beforeSend(request);

        final HttpResponse response = client.execute(request);
        final int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode == NOT_FOUND) return null;
        if (statusCode == FORBIDDEN) throw new ForbiddenException();
        if (!RestResponse.isSuccess(statusCode)) die("getFile("+url+"): error: "+statusCode);

        final HttpEntity entity = response.getEntity();
        if (entity == null) die("getFile("+url+"): No entity");

        final File file = File.createTempFile(getClass().getName()+"-", getTempFileSuffix(path, HttpUtil.getContentType(response)), getDefaultTempDir());
        try (InputStream in = entity.getContent()) {
            try (OutputStream out = new FileOutputStream(file)) {
                IOUtils.copyLarge(in, out);
            }
        }

        return file;
    }

    protected String getTempFileSuffix(String path, String contentType) {
        if (empty(contentType)) return ".temp";
        switch (contentType) {
            case "image/jpeg": return ".jpg";
            case "image/gif": return ".gif";
            case "image/png": return ".png";
            default: return ".temp";
        }
    }

    protected HttpRequestBase beforeSend(HttpRequestBase request) {
        if (!empty(token)) {
            final String tokenHeader = getTokenHeader();
            if (empty(tokenHeader)) die("token set but getTokenHeader returned null");
            request.addHeader(tokenHeader, token);
        }
        final Map<String, String> headers = getHeaders();
        if (!empty(headers)) {
            for (Map.Entry<String, String> entry: headers.entrySet()){
                request.addHeader(entry.getKey(), entry.getValue());
            }
        }
        return request;
    }

    public String getTokenHeader() { return null; }

    protected final Stack<String> tokenStack = new Stack<>();
    public void pushToken(String token) {
        synchronized (tokenStack) {
            if (tokenStack.isEmpty()) tokenStack.push(getToken());
            tokenStack.push(token);
            setToken(token);
        }
    }

    /**
     * Pops the current token off the stack.
     * Now the top of the stack is the previous token, so it becomes the active one.
     * @return The current token popped off (NOT the current active token, call getToken() to get that)
     */
    public String popToken() {
        synchronized (tokenStack) {
            tokenStack.pop();
            setToken(tokenStack.peek());
            return getToken();
        }
    }

    public static final String LOCATION_HEADER = LOCATION;
    private String getLocationHeader(HttpResponse response) {
        final Header header = response.getFirstHeader(LOCATION_HEADER);
        return header == null ? null : header.getValue();
    }

    // call our own copy constructor, return new instance that is a copy of ourselves
    public ApiClientBase copy() { return ReflectionUtil.copy(this); }

    public HttpResponseBean getResponse(HttpRequestBean request) throws IOException {
        if (!request.hasHeader(CONTENT_TYPE)) request.setHeader(CONTENT_TYPE, APPLICATION_OCTET_STREAM);
        final HttpRequestBean realRequest = new HttpRequestBean(request);
        realRequest.setUri(getBaseUri()+request.getUri());
        realRequest.setHeader(getTokenHeader(), getToken());
        return HttpUtil.getResponse(realRequest);
    }

    public InputStream getStream(HttpRequestBean request) throws IOException {
        return HttpUtil.get(getBaseUri()+request.getUri(), new SingletonMap<>(getTokenHeader(), getToken()));
    }

    public String getStreamedString(HttpRequestBean request) throws IOException {
        try {
            @Cleanup InputStream in = getStream(request);
            if (in == null) throw new NotFoundException(request, null);
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            IOUtils.copyLarge(in, out);
            return new String(out.toByteArray());
        } catch (FileNotFoundException e) {
            throw new NotFoundException(request, null);
        }
    }

    @Override public void close() { closeQuietly(httpClient); }

}
