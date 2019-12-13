package org.cobbzilla.wizard.server.config.factory;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

@AllArgsConstructor
public class StringConfigurationSource implements ConfigurationSource {

    @Getter private String value;

    @Override public InputStream getYaml() throws IOException {
        return new ByteArrayInputStream(getValue().getBytes());
    }

}
