package org.cobbzilla.wizard.stream;

import lombok.Getter;
import org.apache.commons.codec.binary.Base64InputStream;
import org.apache.commons.io.IOUtils;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.StreamingOutput;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.string.StringUtil.ellipsis;

public class DataUrlStreamingOutput implements StreamingOutput {

    public static final String DATA_URL_PREFIX = "data:";

    @Getter private final String contentType;
    private final boolean base64;
    private final InputStream data;

    public DataUrlStreamingOutput(String dataUrl) {

        if (!dataUrl.startsWith(DATA_URL_PREFIX)) die("DataUrlStreamingOutput: url does not start with 'data:' : "+ ellipsis(dataUrl, 50));
        final int commaPos = dataUrl.indexOf(",");
        if (commaPos == -1) die("DataUrlStreamingOutput: no comma found in data url: "+ ellipsis(dataUrl, 50));
        if (commaPos == dataUrl.length()-1) die("DataUrlStreamingOutput: no data found after comma data url: "+ ellipsis(dataUrl, 50));
        final String mediaSpecifier = dataUrl.substring(DATA_URL_PREFIX.length(), commaPos);
        final int b64pos = mediaSpecifier.indexOf(";base64");

        final byte[] dataBytes = dataUrl.substring(commaPos).getBytes();
        if (b64pos == -1) {
            contentType = mediaSpecifier;
            base64 = false;
            data = new ByteArrayInputStream(dataBytes);
        } else {
            contentType = mediaSpecifier.substring(0, b64pos);
            base64 = true;
            data = new Base64InputStream(new ByteArrayInputStream(dataBytes), false);
        }
    }

    @Override public void write(OutputStream output) throws IOException, WebApplicationException {
        IOUtils.copyLarge(data, output);
    }

}
