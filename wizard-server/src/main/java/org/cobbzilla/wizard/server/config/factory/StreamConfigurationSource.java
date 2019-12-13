package org.cobbzilla.wizard.server.config.factory;

import lombok.AllArgsConstructor;

import java.io.IOException;
import java.io.InputStream;

@AllArgsConstructor
public class StreamConfigurationSource implements ConfigurationSource {

    public StreamConfigurationSource (String resourcePath) {
        this(StreamConfigurationSource.class.getClassLoader().getResourceAsStream(resourcePath));
    }

    private InputStream stream;

    @Override public InputStream getYaml() throws IOException { return stream; }

    public static ConfigurationSource fromResource (Class clazz, String stream) {
        InputStream in = clazz.getResourceAsStream(stream);
        if (in == null) in = clazz.getClassLoader().getResourceAsStream(stream);
        if (in == null) throw new IllegalArgumentException("StreamConfigurationSource.fromResources: Couldn't find stream: "+stream);
        return new StreamConfigurationSource(in);
    }

}
