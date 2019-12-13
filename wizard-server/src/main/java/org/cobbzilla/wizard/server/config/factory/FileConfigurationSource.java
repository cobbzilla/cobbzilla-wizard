package org.cobbzilla.wizard.server.config.factory;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@AllArgsConstructor
public class FileConfigurationSource implements ConfigurationSource {

    @Getter private File file;

    @Override public InputStream getYaml() throws IOException {
        return new FileInputStream(getFile());
    }

}
