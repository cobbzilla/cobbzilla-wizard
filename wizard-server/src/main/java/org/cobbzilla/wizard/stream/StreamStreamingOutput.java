package org.cobbzilla.wizard.stream;

import lombok.AllArgsConstructor;
import lombok.Getter;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static org.cobbzilla.util.io.StreamUtil.copyLarge;

@AllArgsConstructor
public class StreamStreamingOutput implements StreamingOutput {

    @Getter private InputStream in;

    @Override public void write(OutputStream out) throws IOException, WebApplicationException {
        if (in != null) copyLarge(in, out);
    }

}
