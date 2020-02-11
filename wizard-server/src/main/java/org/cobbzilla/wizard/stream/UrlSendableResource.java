package org.cobbzilla.wizard.stream;

import org.cobbzilla.util.collection.NameAndValue;

import static org.cobbzilla.util.http.HttpContentTypes.TEXT_PLAIN;
import static org.cobbzilla.util.http.URIUtil.getPath;
import static org.cobbzilla.util.io.FileUtil.basename;

public class UrlSendableResource extends SendableResource {

    public UrlSendableResource(String url) { this(url, false); }

    public UrlSendableResource(String url, boolean base64) {
        super(new UrlStreamingOutput(url, base64));
        final UrlStreamingOutput urlOut = (UrlStreamingOutput) getOut();
        setStatus(urlOut.getResponse().getStatus());
        setHeaders(urlOut.getResponse().getHeaders().toArray(NameAndValue.EMPTY_ARRAY));
        setName(basename(getPath(url)));
        setContentLength(urlOut.getContentLength());
        setContentType(base64 ? TEXT_PLAIN : urlOut.getResponse().getContentType());
    }

}
