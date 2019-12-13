package org.cobbzilla.wizard.server.config.factory;

import com.github.jknack.handlebars.Handlebars;
import lombok.Cleanup;
import lombok.Getter;
import org.cobbzilla.util.handlebars.HandlebarsUtil;
import org.cobbzilla.util.io.StreamUtil;
import org.cobbzilla.wizard.server.config.RestServerConfiguration;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.reflect.ReflectionUtil.instantiate;

public class RestServerConfigurationFactory<C extends RestServerConfiguration> {

    private final Yaml yaml = new Yaml();

    private final Class<C> configurationClass;

    @Getter(lazy=true)
    private final Handlebars handlebars = initHandlebars();
    private Handlebars initHandlebars() {
        final Handlebars hb = new Handlebars(new HandlebarsUtil(getClass().getSimpleName()));
        instantiate(configurationClass).registerConfigHandlerbarsHelpers(hb);
        return hb;
    }

    public RestServerConfigurationFactory(Class<C> configurationClass) { this.configurationClass = configurationClass; }

    public C build(ConfigurationSource configuration) throws IOException {
        return build(configuration, null);
    }

    public C build(ConfigurationSource configuration, Map<String, String> env) {
        try {
            @Cleanup final InputStream yamlStream = configuration.getYaml();
            final String rawYaml = StreamUtil.toString(yamlStream);
            final String yaml = HandlebarsUtil.apply(
                    getHandlebars(),
                    rawYaml,
                    empty(env) ? Collections.emptyMap() : new HashMap<>(env));
            return this.yaml.loadAs(yaml, configurationClass);

        } catch (Exception e) {
            return die("build: "+e, e);
        }
    }

}
