package org.cobbzilla.wizard.util;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.OutputStream;

@AllArgsConstructor
class ProxyStreamingOutput implements StreamingOutput {

    @Getter private final HttpResponse response;
    @Getter private final CloseableHttpClient httpClient;

    @Override public void write(OutputStream output) throws IOException, WebApplicationException {
        try {
            IOUtils.copy(response.getEntity().getContent(), output);
        } finally {
            httpClient.close();
        }
    }

}
