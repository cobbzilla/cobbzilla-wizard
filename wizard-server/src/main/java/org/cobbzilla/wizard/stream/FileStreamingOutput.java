package org.cobbzilla.wizard.stream;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.cobbzilla.util.io.StreamUtil;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.StreamingOutput;
import java.io.*;

@AllArgsConstructor
public class FileStreamingOutput implements StreamingOutput {

    @Getter private final File file;

    @Override public void write(OutputStream out) throws IOException, WebApplicationException {
        try (InputStream in = new FileInputStream(file)) {
            StreamUtil.copyLarge(in, out);
        }
    }
}
