package org.cobbzilla.wizard.resources;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.cobbzilla.util.io.StreamUtil;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.StreamingOutput;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@AllArgsConstructor
public class ByteStreamingOutput implements StreamingOutput {

    @Getter private final byte[] data;

    @Override public void write(OutputStream out) throws IOException, WebApplicationException {
        try (InputStream in = new ByteArrayInputStream(data)) {
            StreamUtil.copyLarge(in, out);
        }
    }
}
