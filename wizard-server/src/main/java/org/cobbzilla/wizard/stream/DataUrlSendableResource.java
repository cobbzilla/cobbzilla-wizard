package org.cobbzilla.wizard.stream;

public class DataUrlSendableResource extends SendableResource {

    public DataUrlSendableResource(String name, String dataUrl) { this(name, dataUrl, false); }

    public DataUrlSendableResource(String name, String dataUrl, boolean base64) {
        super(new DataUrlStreamingOutput(dataUrl, base64));
        final DataUrlStreamingOutput out = (DataUrlStreamingOutput) getOut();
        setName(name);
        setContentType(out.getContentType());
        setContentLength(out.getContentLength());
    }

}
