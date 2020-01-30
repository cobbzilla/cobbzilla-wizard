package org.cobbzilla.wizard.server.config;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.cache.Refreshable;
import org.cobbzilla.util.http.HttpUtil;
import org.cobbzilla.util.io.FileUtil;
import org.cobbzilla.util.io.StreamUtil;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.concurrent.TimeUnit.DAYS;
import static org.cobbzilla.util.daemon.ZillaRuntime.CLASSPATH_PREFIX;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.http.HttpSchemes.isHttpOrHttps;
import static org.cobbzilla.util.string.StringUtil.chop;
import static org.cobbzilla.util.string.StringUtil.trimQuotes;

@Slf4j
public class LegalInfo {

    public static final String DOC_TERMS = "terms";
    public static final String DOC_PRIVACY = "privacy";
    public static final String DOC_COMMUNITY = "community";
    public static final String DOC_LICENSES = "licenses";

    @Setter private String base;
    public String getBase() { return trimQuotes(base); }

    @Getter @Setter private String termsOfService = "terms.md";
    @Getter @Setter private String privacyPolicy = "privacy.md";
    @Getter @Setter private String communityGuidelines = "community.md";
    @Getter @Setter private String licenses = "licenses.md";

    public String getDocument (String type) { return loaders.get().get(type); }

    public String getTermsOfServiceDocument () { return getDocument(DOC_TERMS); }
    public String getPrivacyPolicyDocument () { return getDocument(DOC_PRIVACY); }
    public String getCommunityGuidelinesDocument () { return getDocument(DOC_COMMUNITY); }
    public String getLicensesDocument () { return getDocument(DOC_LICENSES); }

    public static final long LOADER_CACHE_MILLIS = DAYS.toMillis(30);
    private final Refreshable<Map<String, String>> loaders = new Refreshable<>("loaders", LOADER_CACHE_MILLIS, this::initLoaders);

    @SuppressWarnings("ConstantConditions")
    private ConcurrentHashMap<String, String> initLoaders() {
        final ConcurrentHashMap<String, String> map = new ConcurrentHashMap<>();
        map.put(DOC_TERMS,     load(getTermsOfService()));
        map.put(DOC_PRIVACY,   load(getPrivacyPolicy()));
        map.put(DOC_COMMUNITY, load(getCommunityGuidelines()));
        map.put(DOC_LICENSES,  load(getLicenses()));
        return map;
    }

    public void refresh() { loaders.set(null); }

    private String load(String type) {
        String value = type;
        if (empty(value)) return "";
        if (!empty(getBase())) value = chop(getBase(), "/") + "/" + value;

        if (isHttpOrHttps(value)) {
            try {
                return HttpUtil.getResponse(value).getEntityString();
            } catch (IOException e) {
                log.error("load("+type+"): "+value+": "+e);
                return "";
            }

        } else if (value.startsWith("/") && new File(value).exists()) {
            try {
                return FileUtil.toString(value);
            } catch (Exception e) {
                log.error("load("+type+"): "+value+": "+e);
                return "";
            }
        } else {
            if (value.startsWith(CLASSPATH_PREFIX)) value = value.substring(CLASSPATH_PREFIX.length());
            try {
                return StreamUtil.loadResourceAsString(value);
            } catch (Exception e) {
                log.error("load(" + type + ") unable to load from filesystem or classpath: " + value);
                return "";
            }
        }
    }
}
