package org.cobbzilla.wizard.resources;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.util.collection.ArrayUtil;
import org.cobbzilla.util.collection.NameAndValue;

import javax.ws.rs.core.StreamingOutput;

import static org.cobbzilla.util.http.HttpStatusCodes.OK;

@Accessors(chain=true)
public class SendableResource {

    public SendableResource (StreamingOutput out) { setOut(out); }

    @Getter @Setter private int status = OK;
    @Getter @Setter private String statusReason;
    @Getter @Setter private NameAndValue[] headers;
    @Getter @Setter private StreamingOutput out;
    @Getter @Setter private String name;
    @Getter @Setter private String contentType;
    @Getter @Setter private Long contentLength;
    @Getter @Setter private Boolean forceDownload;

    public SendableResource addHeader (String name, String value) {
        headers = ArrayUtil.append(headers, new NameAndValue(name, value));
        return this;
    }
}
