package org.cobbzilla.wizard.stream;

import lombok.Getter;
import org.apache.commons.io.IOUtils;
import org.cobbzilla.util.http.HttpRequestBean;
import org.cobbzilla.util.http.HttpResponseBean;
import org.cobbzilla.util.http.HttpUtil;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.StreamingOutput;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;

public class UrlStreamingOutput implements StreamingOutput {

    @Getter private HttpResponseBean response;
    private ByteArrayInputStream in;

    public UrlStreamingOutput(String url) {
        try {
            response = HttpUtil.getResponse(new HttpRequestBean(url));
            in = new ByteArrayInputStream(response.getEntity());
        } catch (IOException e) {
            die("UrlStreamingOutput: "+shortError(e));
        }
    }

    @Override public void write(OutputStream output) throws IOException, WebApplicationException {
        IOUtils.copyLarge(in, output);
    }

}
