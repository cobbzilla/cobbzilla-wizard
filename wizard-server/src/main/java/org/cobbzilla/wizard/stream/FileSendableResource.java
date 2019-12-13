package org.cobbzilla.wizard.stream;

import java.io.File;

import static org.cobbzilla.util.http.HttpContentTypes.contentType;

public class FileSendableResource extends SendableResource {

    public FileSendableResource(File file) {
        super(new FileStreamingOutput(file));
        setContentLength(file.length());
        setContentType(contentType(file.getName()));
        setName(file.getName());
    }

}
