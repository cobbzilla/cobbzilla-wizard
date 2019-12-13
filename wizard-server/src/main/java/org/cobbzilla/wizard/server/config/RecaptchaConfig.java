package org.cobbzilla.wizard.server.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;

import java.util.ArrayList;
import java.util.List;

import static org.cobbzilla.util.json.JsonUtil.fromJson;

@NoArgsConstructor @AllArgsConstructor @Slf4j
public class RecaptchaConfig {

    @Getter @Setter private String publicKey;
    @Getter @Setter private String privateKey;

    public static final String RECAPTCHA_VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";

    public boolean verify (String captchaResponse) {
        try {
            return getVerificationResponse(captchaResponse).isSuccess();
        } catch (Exception e) {
            log.error("verify: error checking recaptcha: "+e);
            return false;
        }
    }

    public RecaptchaVerificationResponse getVerificationResponse(String captchaResponse) throws Exception {
        RecaptchaVerificationResponse verificationResponse;
        HttpPost post = null;
        try {
            final HttpClient client = HttpClientBuilder.create().build();
            post = new HttpPost(RECAPTCHA_VERIFY_URL);
            final List<NameValuePair> urlParameters = new ArrayList<>();
            urlParameters.add(new BasicNameValuePair("secret", privateKey));
            urlParameters.add(new BasicNameValuePair("response", captchaResponse));
            post.setEntity(new UrlEncodedFormEntity(urlParameters));

            final HttpResponse httpResponse = client.execute(post);
            verificationResponse = fromJson(httpResponse.getEntity().getContent(), RecaptchaVerificationResponse.class);

        } finally {
            if (post != null) post.releaseConnection();
        }
        return verificationResponse;
    }

    public static class RecaptchaVerificationResponse {
        @Getter @Setter private boolean success;
        @Getter @Setter private String challenge_ts;
        @Getter @Setter private String hostname;
        @JsonProperty(value="error-codes") @Getter @Setter private String[] errorCodes;
    }
}
