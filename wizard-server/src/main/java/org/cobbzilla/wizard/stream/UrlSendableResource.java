package org.cobbzilla.wizard.stream;

import org.cobbzilla.util.collection.NameAndValue;

import static org.cobbzilla.util.http.URIUtil.getPath;
import static org.cobbzilla.util.io.FileUtil.basename;

public class UrlSendableResource extends SendableResource {

    public UrlSendableResource(String url) {
        super(new UrlStreamingOutput(url));
        final UrlStreamingOutput urlOut = (UrlStreamingOutput) getOut();
        setStatus(urlOut.getResponse().getStatus());
        setHeaders(urlOut.getResponse().getHeaders().toArray(NameAndValue.EMPTY_ARRAY));
        setName(basename(getPath(url)));
        setContentLength(urlOut.getResponse().getContentLength());
        setContentType(urlOut.getResponse().getContentType());
    }

}
