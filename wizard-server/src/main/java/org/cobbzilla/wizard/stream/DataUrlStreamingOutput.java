package org.cobbzilla.wizard.stream;

import lombok.Getter;
import org.apache.commons.codec.binary.Base64InputStream;
import org.apache.commons.io.IOUtils;
import org.cobbzilla.util.string.Base64;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.StreamingOutput;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.http.HttpContentTypes.TEXT_PLAIN;
import static org.cobbzilla.util.string.StringUtil.ellipsis;

public class DataUrlStreamingOutput implements StreamingOutput {

    public static final String DATA_URL_PREFIX = "data:";

    @Getter private final String contentType;
    @Getter private final Long contentLength;
    private final InputStream data;

    public static String dataUrl (String contentType, boolean base64, String data) {
        return DATA_URL_PREFIX + contentType + (base64 ? ";base64" : "") + "," + data;
    }

    public static byte[] dataUrlBytes (String contentType, boolean base64, String data) {
        return dataUrl(contentType, base64, data).getBytes();
    }

    public DataUrlStreamingOutput(String dataUrl) { this(dataUrl, false); }

    public DataUrlStreamingOutput(String dataUrl, boolean base64) {

        if (!dataUrl.startsWith(DATA_URL_PREFIX)) die("DataUrlStreamingOutput: url does not start with 'data:' : "+ ellipsis(dataUrl, 50));
        final int commaPos = dataUrl.indexOf(",");
        if (commaPos == -1) die("DataUrlStreamingOutput: no comma found in data url: "+ ellipsis(dataUrl, 50));
        if (commaPos == dataUrl.length()-1) die("DataUrlStreamingOutput: no data found after comma data url: "+ ellipsis(dataUrl, 50));
        final String mediaSpecifier = dataUrl.substring(DATA_URL_PREFIX.length(), commaPos);
        final int b64pos = mediaSpecifier.indexOf(";base64");

        final byte[] urlBytes = dataUrl.getBytes();
        final byte[] dataBytes = dataUrl.substring(commaPos).getBytes();
        if (b64pos == -1) {
            if (base64) {
                // data url was not base64, but base64 was requested -- encode and send
                final byte[] bytes = dataUrlBytes(mediaSpecifier, true, Base64.encodeBytes(dataBytes));
                data = new ByteArrayInputStream(bytes);
                contentType = TEXT_PLAIN;
                contentLength = (long) bytes.length;
            } else {
                // data url was not base64, and base64 was not requested -- send as-is
                data = new ByteArrayInputStream(dataBytes);
                contentType = mediaSpecifier;
                contentLength = (long) dataBytes.length;
            }
        } else {
            if (base64) {
                // data url was base64, and base64 was requested -- send as-is
                data = new ByteArrayInputStream(urlBytes);
                contentType = TEXT_PLAIN;
                contentLength = (long) urlBytes.length;
            } else {
                // data url was base64, and base64 was not requested -- decode and send
                data = new Base64InputStream(new ByteArrayInputStream(dataBytes), false);
                contentType = mediaSpecifier.substring(0, b64pos);
                contentLength = (long) dataBytes.length;
            }
        }
    }

    @Override public void write(OutputStream output) throws IOException, WebApplicationException {
        IOUtils.copyLarge(data, output);
    }

}
