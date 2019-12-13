package org.cobbzilla.wizard.util;

import lombok.AllArgsConstructor;
import org.cobbzilla.util.io.StreamUtil;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.StreamingOutput;
import java.io.*;

@AllArgsConstructor
public class FileStreamingOutput implements StreamingOutput {

    private File cache;

    @Override
    public void write(OutputStream out) throws IOException, WebApplicationException {
        try (InputStream in = new FileInputStream(cache)) {
            StreamUtil.copyLarge(in, out);
        }
    }
}
