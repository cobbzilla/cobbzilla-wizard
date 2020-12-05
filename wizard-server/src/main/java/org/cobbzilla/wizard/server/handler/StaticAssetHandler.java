package org.cobbzilla.wizard.server.handler;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.cobbzilla.util.io.DeleteOnExit;
import org.cobbzilla.util.io.FileUtil;
import org.cobbzilla.util.io.StreamUtil;
import org.cobbzilla.util.json.JsonUtil;
import org.cobbzilla.util.string.StringUtil;
import org.cobbzilla.wizard.server.config.StaticHttpConfiguration;
import org.glassfish.grizzly.http.server.CLStaticHttpHandler;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;

import javax.ws.rs.NotFoundException;
import java.io.File;
import java.io.InputStream;
import java.io.Writer;
import java.util.*;

import static org.cobbzilla.util.daemon.ZillaRuntime.toList;
import static org.cobbzilla.util.io.FileUtil.abs;
import static org.cobbzilla.util.security.ShaUtil.sha256_hex;

@Slf4j
public class StaticAssetHandler extends CLStaticHttpHandler {

    public static final String[] DEFAULT_INDEX_ALIASES = {"/index.php"};
    public static final String RECALC_PARAM = "_flush";

    private StaticHttpConfiguration configuration;
    private Set<String> indexAliases;
    private File assetDirFile;
    private String singlePageApp;

    private File templateFileRoot;

    public StaticAssetHandler(StaticHttpConfiguration configuration, ClassLoader classLoader) {
        super(classLoader, configuration.getAssetRoot());
        this.configuration = configuration;
        if (configuration.hasLocalOverride()) {
            assetDirFile = configuration.getLocalOverride();
            if (assetDirFile.exists() && assetDirFile.canRead()) {
                templateFileRoot = assetDirFile;
            } else {
                log.warn("asset dir ("+abs(assetDirFile)+") does not exist, all resources will be served from classpath");
            }
        }
        this.singlePageApp = configuration.getSinglePageApp();

        // everything will load from the classpath.
        // create a dummy location where no templates will be found.
        if (templateFileRoot == null) {
            templateFileRoot = FileUtil.createTempDirOrDie(RandomStringUtils.randomAlphanumeric(20));
            DeleteOnExit.add(templateFileRoot);
        }

        final Map<String, String> utilPaths = configuration.getUtilPaths();
        if (utilPaths.containsKey(StaticUtilPath.INDEX_ALIASES.name())) {
            indexAliases = new HashSet<>(Arrays.asList(utilPaths.get(StaticUtilPath.INDEX_ALIASES.name()).split(":")));
        } else {
            indexAliases = new HashSet<>(Arrays.asList(DEFAULT_INDEX_ALIASES));
        }
        indexAliases.add("/"); // lonely slash always goes to index
        indexAliases.add("");  // empty path always goes to index
    }

    @Override
    protected boolean handle(String resourcePath, Request request, Response response) throws Exception {
        if (indexAliases.contains(resourcePath)) {
            return this.handle(getUtilPath(StaticUtilPath.INDEX_PATH, "/index.html"), request, response);
        }

        // remove double slashes
        while (resourcePath.contains("//")) resourcePath = resourcePath.replace("//", "/");

        if (isUtilPath(resourcePath, StaticUtilPath.REQUEST_HEADERS_JS, "/js/request_headers.js")) {
            final Writer writer = response.getWriter();
            final String data = getRequestHeaderJavascript(request);
            writer.write(data);
            return true;
        }

        if (isUtilPath(resourcePath, StaticUtilPath.LOCALE, "locale")) {
            final Writer writer = response.getWriter();
            final List<Locale> locales = request.getLocales();
            final String data;
            if (locales.size() > 0) {
                data = "{'locale': '"+request.getLocale()+"', 'locales': ['"+ StringUtil.toString(locales, "', '") + "']}";
            } else {
                data = "{}";
            }
            writer.write(data);
            return true;
        }
        if (resourcePath.endsWith(".html/")) {
            // strip trailing slash
            resourcePath = resourcePath.substring(0, resourcePath.length()-1);
        } else if (resourcePath.contains("/index.html/")) {
            // if URI is in the form "/{pre-path}/index.html/{post-path}", then remove "index.html/"
            resourcePath = resourcePath.replace("/index.html/", "/");
        }

        // ENV takes precedence
        if (assetDirFile != null && assetDirFile.exists()) {
            File file = new File(assetDirFile, resourcePath);
            if (file.exists()) {
                if (file.isDirectory()) {
                    response.sendRedirect("index.html");
                    return true;
                }
                final Map<String, String> substitutions = configuration.getSubstitutions(resourcePath);
                if (substitutions != null && !substitutions.isEmpty()) {
                    file = substitute(file, substitutions, request.getParameter(RECALC_PARAM) != null);
                }
                sendFile(response, file);
                return true;

            } else if (singlePageApp != null) {
                if (resourcePath.equals(singlePageApp)) {
                    log.info("handle: avoiding infinite recursion (calling super): singlePageApp == resourcePath == "+resourcePath);
                    return super.handle(resourcePath, request, response);
                }
                final int lastSlash = resourcePath.lastIndexOf('/');
                if (lastSlash != 0) {
                    return handle(resourcePath.substring(lastSlash), request, response);
                } else {
                    return handle(singlePageApp, request, response);
                }

            } else {
                log.info("handle: resource "+resourcePath+" not found in override dir ("+abs(assetDirFile)+"), using default from classpath");
            }
        }

        if (singlePageApp != null) {
            // Determine full path to resource, we must ensure it exists
            String path = configuration.getAssetRoot()+resourcePath;

            // Remove double slashes
            while (path.contains("//")) path = path.replace("//", "/");

            final InputStream in = getClass().getClassLoader().getResourceAsStream(path);
            if (in != null) {
                // Resource exists, serve it normally
                in.close();
            } else {
                // Resource does not exist, serve the singlePageApp resource, unless this is a recursive request
                if (resourcePath.equals(singlePageApp)) {
                    log.warn("handle: infinite recursion would occur (returning Not Found): singlePageApp == resourcePath == " + resourcePath);
                    throw new NotFoundException(resourcePath);
                }
                final int lastSlash = resourcePath.lastIndexOf('/');
                if (lastSlash != 0) {
                    return handle(resourcePath.substring(lastSlash), request, response);
                } else {
                    return handle(singlePageApp, request, response);
                }
            }
        }

        final Map<String, String> substitutions = configuration.getSubstitutions(resourcePath);
        if (substitutions != null && !substitutions.isEmpty()) {
            String path = configuration.getAssetRoot() + resourcePath;
            while (path.contains("//")) path = path.replace("//", "/");
            final File f = StreamUtil.loadResourceAsFile(path);
            f.deleteOnExit();
            final File subst = substitute(f, substitutions, request.getParameter(RECALC_PARAM) != null);
            subst.deleteOnExit();
            sendFile(response, subst);
            return true;
        } else {
            return super.handle(resourcePath, request, response);
        }
    }

    protected File substitute(File file, Map<String, String> substitutions, boolean recalc) {
        final StringBuilder b = new StringBuilder(abs(file)).append(":");
        for (Map.Entry<String, String> entry : substitutions.entrySet()) {
            b.append(entry.getKey()).append("=").append(entry.getValue()).append(":");
        }
        final String cacheKey = sha256_hex(b.toString());
        final File cached = new File(configuration.getSubstitutionCacheDir(), cacheKey);
        final String delim = configuration.getSubstitutionDelimiter();
        if (!cached.exists() || recalc) {
            try {
                String data = FileUtil.toString(file);
                for (Map.Entry<String, String> entry : substitutions.entrySet()) {
                    data = data.replace(delim + entry.getKey() + delim, entry.getValue());
                }
                FileUtil.toFile(cached, data);
            } catch (Exception e) {
                log.error("substitute: Error generating/writing substitutions (returning unsubstituted file): "+e);
                return file;
            }
        }
        return cached;
    }

    private String getUtilPath(StaticUtilPath utilPath, String defaultPath) {
        String path = configuration.getUtilPaths().get(utilPath.name());
        return (path != null) ? path : defaultPath;
    }

    private boolean isUtilPath(String resourcePath, StaticUtilPath utilPath, String defaultPath) {
        final String path = getUtilPath(utilPath, defaultPath);
        return resourcePath.equals(path);
    }

    private String getRequestHeaderJavascript(Request request) throws Exception {
        Map<String, Object> headers = new HashMap<>();
        for (String name : request.getHeaderNames()) {
            List<String> values = toList(request.getHeaders(name).iterator());
            if (values.size() == 1) {
                headers.put(name, values.get(0));
            } else {
                headers.put(name, values);
            }
        }
        return "REQUEST_HEADERS = " + JsonUtil.toJson(headers);
    }

}
