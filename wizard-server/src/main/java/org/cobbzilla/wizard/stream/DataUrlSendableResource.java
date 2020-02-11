package org.cobbzilla.wizard.stream;

public class DataUrlSendableResource extends SendableResource {

    public DataUrlSendableResource(String name, String dataUrl) {
        super(new DataUrlStreamingOutput(dataUrl));
        final DataUrlStreamingOutput out = (DataUrlStreamingOutput) getOut();
        setName(name);
        setContentType(out.getContentType());
    }

}
