package org.cobbzilla.wizard.server.config.factory;

import java.io.IOException;
import java.io.InputStream;

public interface ConfigurationSource {

    InputStream getYaml() throws IOException;

}
