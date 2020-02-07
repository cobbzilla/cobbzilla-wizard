package org.cobbzilla.wizard.filters;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.SingletonList;
import org.cobbzilla.util.string.StringUtil;
import org.cobbzilla.wizard.cache.redis.RedisService;
import org.cobbzilla.wizard.server.config.RestServerConfiguration;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Context;
import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.http.HttpStatusCodes.TOO_MANY_REQUESTS;
import static org.cobbzilla.util.io.StreamUtil.stream2string;
import static org.cobbzilla.util.string.StringUtil.getPackagePath;
import static org.cobbzilla.wizard.resources.ResourceUtil.status;

@NoArgsConstructor @Slf4j
public abstract class RateLimitFilter implements ContainerRequestFilter {

    @Autowired public RestServerConfiguration configuration;
    @Autowired public RedisService redis;

    @Getter(lazy=true) private final RedisService cache = initCache();
    private RedisService initCache() { return redis.prefixNamespace(getClass().getSimpleName()); }

    @Getter(lazy=true) private final String scriptSha = initScript();
    public String initScript() {
        return getCache().loadScript(stream2string(getPackagePath(RateLimitFilter.class)+"/api_limiter_redis.lua"));
    }

    @Getter private final static LoadingCache<String, List<String>> keys =
            CacheBuilder.newBuilder()
                        .maximumSize(1000)
                        .expireAfterAccess(5, TimeUnit.MINUTES)
                        .build(new CacheLoader<String, List<String>>() {
                            public List<String> load(String key) { return new SingletonList<>(key); }
                        });

    protected String getToken(ContainerRequestContext request) {
        return request.getHeaderString("X-Forwarded-For");
    }

    protected List<String> getKeys(ContainerRequestContext request) {
        String key;
        final Principal user = empty(request.getSecurityContext()) ? null : request.getSecurityContext().getUserPrincipal();
        if (!empty(user)) {
            if (allowUnlimitedUse(user, request)) {
                if (log.isTraceEnabled()) log.trace("getKeys: unlimited use permitted (user="+user+", request.uri=" + request.getUriInfo().getRequestUri().toString() + "), returning null (no keys)");
                return null;
            }
            key = user.getName();

        } else if (allowUnlimitedUse(null, request)) {
            if (log.isTraceEnabled()) log.trace("getKeys: (empty user) unlimited use permitted (request.uri=" + request.getUriInfo().getRequestUri().toString() + "), returning null (no keys)");
            return null;

        } else {
            final String token = getToken(request);
            if (!empty(token)) key = token;
            else {
                final String ip = request.getHeaderString("X-Forwarded-For");
                key = empty(ip) ? "0.0.0.0" : ip;
            }
        }
        try {
            return getKeys().get(getCache().prefix(key));
        } catch (ExecutionException e) {
            return new SingletonList<>(key);
        }
    }

    protected boolean allowUnlimitedUse(Principal user, ContainerRequestContext request) { return false; }

    @Getter(lazy=true) private final List<ApiRateLimit> limits = initLimits();
    private List<ApiRateLimit> initLimits() {
        return configuration.hasRateLimits() ? Arrays.asList(configuration.getRateLimits()) : null;
    }

    @Getter(lazy=true) private final List<String> limitsAsStrings = initLimitsAsStrings();
    protected List<String> initLimitsAsStrings() {
        final List<ApiRateLimit> limits = getLimits();
        if (empty(limits)) return null;
        return limits.stream().map(x->new String[] {
                String.valueOf(x.getLimit()),
                String.valueOf(x.getIntervalDuration()),
                String.valueOf(x.getBlockDuration())
        }).flatMap(Arrays::stream).collect(Collectors.toList());
    }

    @Override public void filter(@Context ContainerRequestContext request) {

        if (getLimitsAsStrings() == null) return; // noop

        final List<String> keys = getKeys(request);
        if (keys == null || keys.isEmpty()) return; // noop
        final Long i = (Long) getCache().eval(getScriptSha(), keys, getLimitsAsStrings());
        if (i != null) {
            final List<ApiRateLimit> limits = getLimits();
            if (i < 0 || i >= limits.size()) {
                log.warn("filter: unknown limit ("+i+") exceeded for keys: "+StringUtil.toString(keys)+" with url="+request.getUriInfo().getRequestUri().toString());
            } else {
                log.warn("filter: limit ("+limits.get(i.intValue())+") exceeded for keys: "+StringUtil.toString(keys)+" with url="+request.getUriInfo().getRequestUri().toString());
            }
            throw new WebApplicationException(status(TOO_MANY_REQUESTS));

        } else if (log.isTraceEnabled()) {
            log.trace("filter: incrementing counter for keys: "+StringUtil.toString(keys)+" with url="+request.getUriInfo().getRequestUri().toString());
        }
    }

}
