package org.cobbzilla.wizard.stream;

import lombok.Getter;
import org.apache.commons.io.IOUtils;
import org.cobbzilla.util.http.HttpRequestBean;
import org.cobbzilla.util.http.HttpResponseBean;
import org.cobbzilla.util.http.HttpUtil;
import org.cobbzilla.util.string.Base64;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.StreamingOutput;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;
import static org.cobbzilla.wizard.stream.DataUrlStreamingOutput.dataUrlBytes;

public class UrlStreamingOutput implements StreamingOutput {

    @Getter private HttpResponseBean response;
    private ByteArrayInputStream in;
    @Getter private long contentLength;

    public UrlStreamingOutput(String url) { this(url, false); }

    public UrlStreamingOutput(String url, boolean base64) {
        try {
            response = HttpUtil.getResponse(new HttpRequestBean(url));
            if (base64) {
                final String b64data = Base64.encodeBytes(response.getEntity());
                final byte[] b64bytes = dataUrlBytes(response.getContentType(), true, b64data);
                this.in = new ByteArrayInputStream(b64bytes);
                this.contentLength = b64bytes.length;
            } else {
                this.in = new ByteArrayInputStream(response.getEntity());
                this.contentLength = response.getEntity().length;
            }
        } catch (IOException e) {
            die("UrlStreamingOutput: "+shortError(e));
        }
    }

    @Override public void write(OutputStream output) throws IOException, WebApplicationException {
        IOUtils.copyLarge(in, output);
    }

}
