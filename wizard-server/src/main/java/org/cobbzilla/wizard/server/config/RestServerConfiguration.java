package org.cobbzilla.wizard.server.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.jknack.handlebars.Handlebars;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.cobbzilla.util.collection.ExpirationMap;
import org.cobbzilla.util.io.FileUtil;
import org.cobbzilla.wizard.analytics.AnalyticsConfiguration;
import org.cobbzilla.wizard.analytics.AnalyticsHandler;
import org.cobbzilla.wizard.dao.CacheFlushable;
import org.cobbzilla.wizard.dao.DAO;
import org.cobbzilla.wizard.filters.ApiRateLimit;
import org.cobbzilla.wizard.log.LogRelayAppenderConfig;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.IdentifiableBase;
import org.cobbzilla.wizard.resources.ParentResource;
import org.cobbzilla.wizard.server.RestServer;
import org.cobbzilla.wizard.util.SpringUtil;
import org.cobbzilla.wizard.validation.Validator;
import org.springframework.context.ApplicationContext;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.http.HttpSchemes.SCHEME_HTTP;
import static org.cobbzilla.util.network.NetworkUtil.getLocalhostIpv4;
import static org.cobbzilla.util.reflect.ReflectionUtil.forName;
import static org.cobbzilla.util.reflect.ReflectionUtil.instantiate;

@Slf4j
public class RestServerConfiguration {

    public static final int MAX_DUMP_TRIES = 5;

    @JsonIgnore @Getter @Setter RestServer server;
    public boolean isRunning() { return getServer() != null && getServer().isRunning(); }

    // subclasses may override this to add more helpers
    // this is only for the Handlebars used to load this config itself
    public void registerConfigHandlerbarsHelpers(Handlebars handlebars) {}

    @Getter @Setter private Map<String, String> environment = new HashMap<>();
    public Map<String, Object> getEnvCtx() { return new HashMap<>(environment); }

    @Getter @Setter private File tmpdir = FileUtil.getDefaultTempDir();
    @Getter @Setter private String serverName;
    @Getter @Setter private String version;
    public boolean hasVersion () { return !empty(getVersion()); }

    @Setter private String publicUriBase;
    public String getPublicUriBase () {
        if (empty(publicUriBase)) return SCHEME_HTTP+getLocalhostIpv4()+":"+getHttp().getPort();
        return !empty(publicUriBase) && publicUriBase.endsWith("/") ? publicUriBase.substring(0, publicUriBase.length()-1) : publicUriBase;
    }

    @Getter @Setter private OpenApiConfiguration openApi;
    public boolean hasOpenApi () { return openApi != null && openApi.valid(); }

    @Getter @Setter private String springContextPath = "classpath:/spring.xml";
    @Getter @Setter private String springShardContextPath = "classpath:/spring-shard.xml";
    @Getter @Setter private int bcryptRounds = 12;

    @Getter @Setter private Boolean testMode;
    public boolean testMode() { return testMode != null && testMode; }

    @Getter @Setter private LogRelayAppenderConfig logRelay;

    private String appendPathToUriBase(String base, String... pathParts) {
        final StringBuilder b = new StringBuilder(base.endsWith("/") ? base.substring(0, base.length()-1) : base);
        for (String path : pathParts) {
            if (!path.startsWith("/")) b.append("/");
            b.append(path.endsWith("/") ? path.substring(0, path.length()-1) : path);
        }
        return b.toString();
    }

    public String uri(String path) { return appendPathToUriBase(getPublicUriBase(), path); }
    public String api(String path) { return appendPathToUriBase(getApiUriBase(), path); }

    @Getter @Setter private HttpConfiguration http;
    @Getter @Setter private JerseyConfiguration jersey;

    @Getter @Setter private ErrorApiConfiguration errorApi;
    public boolean hasErrorApi () { return errorApi != null && errorApi.isValid(); }

    @JsonIgnore @Getter @Setter private ApplicationContext applicationContext;

    public <T> T autowire (T bean) { return SpringUtil.autowire(applicationContext, bean); }
    public <T> T getBean (Class<T> clazz) { return SpringUtil.getBean(applicationContext, clazz); }
    public <T> T getBean (String clazz) { return (T) SpringUtil.getBean(applicationContext, forName(clazz)); }
    public <T> Map<String, T> getBeans (Class<T> clazz) { return SpringUtil.getBeans(applicationContext, clazz); }

    @Getter @Setter private StaticHttpConfiguration staticAssets;
    public boolean hasStaticAssets () { return staticAssets != null && staticAssets.hasAssetRoot(); }

    @Getter @Setter private HttpHandlerConfiguration[] handlers;
    public boolean hasHandlers () { return !empty(handlers); }

    @Getter @Setter private WebappConfiguration[] webapps;
    public boolean hasWebapps () { return !empty(webapps); }

    @JsonIgnore @Getter @Setter private Validator validator;

    @Getter @Setter private ApiRateLimit[] rateLimits;
    public boolean hasRateLimits () { return !empty(rateLimits); }

    @Getter @Setter private AnalyticsConfiguration analytics;

    @JsonIgnore @Getter(lazy=true) private final AnalyticsHandler analyticsHandler = initAnalyticsHandler();
    private AnalyticsHandler initAnalyticsHandler() {
        if (analytics == null || !analytics.valid()) return null;
        final AnalyticsHandler handler = instantiate(analytics.getHandler());
        handler.init(analytics);
        return handler;
    }

    @Getter @Setter private SupportInfo support = new SupportInfo();
    public boolean getHasSupportInfo () { return support != null && support.getHasInfo(); }

    public String getApiUriBase() { return getPublicUriBase() + getHttp().getBaseUri(); }

    public String getLoopbackApiBase() { return "http://127.0.0.1:" + getHttp().getPort() + getHttp().getBaseUri(); }

    private final Map<String, Map<String, Object>> subResourceCaches = new ConcurrentHashMap<>();
    @Getter @Setter private long subResourceCacheExpiration = TimeUnit.HOURS.toMillis(2);

    /**
     * Allows reuse of subresources, each instantiated with a particular set of immutable objects.
     * @param <R> the type of resource to return, so method calls can be typesafe.
     */
    public <R> Map<String, R> getSubResourceCache(Class<R> resourceClass) {
        Map cache = subResourceCaches.get(resourceClass.getName());
        if (cache == null) {
            synchronized (subResourceCaches) {
                cache = subResourceCaches.get(resourceClass.getName());
                if (cache == null) {
                    cache = new ExpirationMap(getSubResourceCacheExpiration());
                    subResourceCaches.put(resourceClass.getName(), cache);
                }
            }
        }
        return cache;
    }

    public <R> R subResource(Class<R> resourceClass, Object... args) {
        final StringBuilder cacheKey = new StringBuilder(resourceClass.getName()).append(":").append(hashCode());
        final boolean hasArgs = args != null && args.length > 0;
        if (hasArgs) {
            for (Object o : args) {
                if (o == null) {
                    log.warn("forContext("+ArrayUtils.toString(args)+"): null arg");
                    continue;
                }

                if (o instanceof Identifiable) {
                    cacheKey.append(":").append(o.getClass().getName()).append("(").append(((Identifiable) o).getUuid()).append(")");
                    if (o instanceof IdentifiableBase) cacheKey.append("(").append(((IdentifiableBase) o).getMtime()).append(")");
                } else if (o instanceof String) {
                    cacheKey.append(":").append(o.getClass().getName()).append("(").append(o).append(")");
                } else if (o instanceof Number) {
                    cacheKey.append(":").append(o.getClass().getName()).append("(").append(o).append(")");
                } else if (o instanceof ParentResource) {
                    cacheKey.append(":").append(o.getClass().getName()).append("(").append(o).append(")");
                } else if (!(o instanceof DAO)) {
                    log.warn("forContext("+ArrayUtils.toString(args)+"): expected Identifiable or DAO, found "+o.getClass().getName()+": "+o);
                }
            }
        }
        final Map<String, R> resourceCache = getSubResourceCache(resourceClass);
        R r = resourceCache.get(cacheKey.toString());
        if (r == null) {
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (resourceCache) {
                r = resourceCache.get(cacheKey.toString());
                if (r == null) {
                    try {
                        r = autowire(hasArgs ? instantiate(resourceClass, args) : instantiate(resourceClass));
                    } catch (Exception e) {
                        return die("subResource: "+e, e);
                    }
                    resourceCache.put(cacheKey.toString(), r);
                }
            }
        }
        return r;
    }

    private final Map<String, DAO<? extends Identifiable>> daoCache = new ConcurrentHashMap<>();

    @JsonIgnore @Getter(lazy=true) private final Collection<DAO> allDAOs = initAllDAOs();
    private Collection<DAO> initAllDAOs() { return getBeans(DAO.class).values(); }

    public DAO getDaoForEntityClass(Class entityClass) {
        final String name = entityClass.getName();
        return daoCache.computeIfAbsent(name, k -> getBean(name.replace(".model.", ".dao.") + "DAO"));
    }

    public DAO getDaoForEntityClass(String className) {
        return daoCache.computeIfAbsent(className, k -> {
            for (DAO dao : getAllDAOs()) {
                if ( dao.getEntityClass().getSimpleName().equalsIgnoreCase(className) ||
                     dao.getEntityClass().getName().equalsIgnoreCase(className) ) {
                    return dao;
                }
            }
            return die("getDaoForEntityClass("+className+"): DAO not found");
        });
    }

    public void flushDAOs() {
        if (!getServer().isRunning() && applicationContext == null) {
            log.warn("flushDAOs: server not running, cannot enumerate DAOs, quietly returning");
            return;
        }
        try {
            for (DAO dao : getAllDAOs()) {
                if (dao instanceof CacheFlushable) ((CacheFlushable) dao).flush();
            }
        } catch (Exception e) {
            log.warn("flushDAOs: error: "+e.getClass().getSimpleName()+": "+e.getMessage());
        }
    }
}
