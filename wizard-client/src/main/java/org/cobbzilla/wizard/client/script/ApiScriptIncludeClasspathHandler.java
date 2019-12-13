package org.cobbzilla.wizard.client.script;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.nio.file.Paths;

import static org.cobbzilla.util.io.StreamUtil.stream2string;

@Accessors(chain=true)
public class ApiScriptIncludeClasspathHandler implements ApiScriptIncludeHandler {

    @Getter @Setter private String includePrefix;
    @Getter @Setter private String commonPath;

    @Override public String include(String path) {
        final String fileName = path + ".json";
        try {
            return stream2string(Paths.get(getIncludePrefix(), fileName).toString());
        } catch (IllegalArgumentException e) {
            if (getCommonPath() == null) throw e;
            return stream2string(Paths.get(getCommonPath(), fileName).toString());
        }
    }

}
