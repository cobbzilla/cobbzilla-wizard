package org.cobbzilla.wizard.client.script;

import org.cobbzilla.util.string.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;
import static org.cobbzilla.util.io.StreamUtil.loadResourceAsString;
import static org.cobbzilla.util.io.StreamUtil.stream2string;

public interface ApiScriptIncludeHandler {

    Logger log = LoggerFactory.getLogger(ApiScriptIncludeHandler.class);
    List<String> DEFAULT_INCLUDE_PATHS = Arrays.asList("", "models", "tests", "include", "models/tests", "models/include");

    default List<String> getIncludePaths() { return DEFAULT_INCLUDE_PATHS; }

    default String include (String path) {
        final String fileName = path + ".json";
        for (String inc : getIncludePaths()) {
            try {
                return stream2string(Paths.get(inc, fileName).toString());
            } catch (Exception e) {
                try {
                    return loadResourceAsString(inc + "/" + fileName);
                } catch (Exception e2) {
                    log.debug("include(" + path + "): not found in " + inc+": (e="+ shortError(e)+", e2="+ shortError(e2)+")");
                }
            }
        }
        return die("include("+path+"): not found anywhere in "+ StringUtil.toString(getIncludePaths()));
    }

}
