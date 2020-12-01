package org.cobbzilla.wizard.resources;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.cache.AutoRefreshingReference;
import org.cobbzilla.util.string.StringUtil;
import org.cobbzilla.wizard.dao.AbstractCRUDDAO;
import org.cobbzilla.wizard.dao.DAO;
import org.cobbzilla.wizard.model.Identifiable;
import org.cobbzilla.wizard.model.entityconfig.EntityConfig;
import org.cobbzilla.wizard.model.entityconfig.EntityConfigSource;
import org.cobbzilla.wizard.model.entityconfig.EntityFieldConfig;
import org.cobbzilla.wizard.model.entityconfig.annotations.ECType;
import org.cobbzilla.wizard.model.search.SqlViewSearchResult;
import org.cobbzilla.wizard.server.config.PgRestServerConfiguration;
import org.cobbzilla.wizard.util.ClasspathScanner;
import org.glassfish.jersey.server.ContainerRequest;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.daemon.ZillaRuntime.shortError;
import static org.cobbzilla.util.http.HttpStatusCodes.SC_NOT_FOUND;
import static org.cobbzilla.util.http.HttpStatusCodes.SC_OK;
import static org.cobbzilla.util.io.StreamUtil.loadResourceAsStream;
import static org.cobbzilla.util.json.JsonUtil.FULL_MAPPER_ALLOW_COMMENTS;
import static org.cobbzilla.util.json.JsonUtil.fromJson;
import static org.cobbzilla.util.reflect.ReflectionUtil.forName;
import static org.cobbzilla.util.string.StringUtil.packagePath;
import static org.cobbzilla.wizard.resources.ResourceUtil.*;
import static org.cobbzilla.wizard.server.config.OpenApiConfiguration.API_TAG_UTILITY;
import static org.cobbzilla.wizard.server.config.OpenApiConfiguration.SEC_API_KEY;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Slf4j
public abstract class AbstractEntityConfigsResource implements EntityConfigSource {

    public static final String ENTITY_CONFIG_BASE = "entity-config";

    protected abstract PgRestServerConfiguration getConfiguration();

    protected long getConfigRefreshInterval() { return TimeUnit.DAYS.toMillis(30); }
    protected abstract boolean authorized(ContainerRequest ctx);
    protected File getLocalConfig(EntityConfig name) { return null; }

    public static final AtomicReference<Map<String, EntityConfig>> lastConfig = new AtomicReference<>();

    @Getter(AccessLevel.PROTECTED) private final EntityConfigsMap configs = new EntityConfigsMap();
    public boolean refresh() { return refresh(configs); }
    public boolean refresh(AutoRefreshingReference<Map<String, EntityConfig>> configsToReset) {
        configsToReset.flush();
        return true;
    }

    @GET
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags={API_TAG_UTILITY},
            summary="Read entity configs. Returns an array of Strings, each an entity type. When param 'full' is true, response is a Map of entity type names to the full EntityConfig object for each type.",
            description="Read entity configs. Returns an array of Strings, each an entity type. When param 'full' is true, response is a Map of entity type names to the full EntityConfig object for each type.",
            parameters={@Parameter(name="full", description="return all configs")},
            responses={@ApiResponse(responseCode=SC_OK, description="the name of the entity types, or a map of all configs",
                    content={@Content(mediaType=APPLICATION_JSON, examples={
                            @ExampleObject(name="an array of entity type names", value="[\"SomeEntity\", \"AnotherEntity\"]"),
                            @ExampleObject(name="when 'full' param  is passed, returns map of name->config", value="{\"SomeEntity\": {\"entity-config-fields\": \"would-be-here\"}, \"AnotherEntity\": {\"entity-config-fields\": \"would-be-here\"}}")
                    }
                    )})
            }
    )
    public Response getConfigs(@Context ContainerRequest ctx,
                               @QueryParam("full") Boolean full) {
        if (!authorized(ctx)) return forbidden();
        return ok(full != null && full ? getConfigs().getEntries() : getConfigs().get().keySet());
    }

    @Override public EntityConfig getOrCreateEntityConfig(Object thing) throws Exception {
        final EntityConfig entityConfig = getEntityConfig(thing);
        return entityConfig != null ? entityConfig : getEntityConfig(toClass(thing), false);
    }

    @Override public EntityConfig getEntityConfig(Object thing) {
        final AutoRefreshingReference<Map<String, EntityConfig>> configs = getConfigs();
        final Map<String, EntityConfig> configMap = configs.get();
        synchronized (configMap) {
            Class<?> clazz = toClass(thing);
            do {
                final EntityConfig entityConfig = configMap.get(clazz.getName().toLowerCase());
                if (entityConfig != null) return entityConfig;
                clazz = clazz.getSuperclass();
            } while (!clazz.equals(Object.class));
        }
        return null;
    }

    public Class<?> toClass(Object thing) {
        return thing instanceof Class ? (Class<?>) thing : thing.getClass();
    }

    @GET @Path("/{name}")
    @Operation(security=@SecurityRequirement(name=SEC_API_KEY),
            tags={API_TAG_UTILITY},
            summary="Read the entity config for an entity type. Type names are case-insensitive.",
            description="Read the entity config for an entity type. Type names are case-insensitive.",
            parameters={@Parameter(name="name", description="name of the entity type. names are case-insensitive.")},
            responses={
                    @ApiResponse(responseCode=SC_OK, description="the EntityConfig object for the type"),
                    @ApiResponse(responseCode=SC_NOT_FOUND, description = "no EntityConfig exists with the name given")
            }
    )
    public Response getConfig (@Context ContainerRequest ctx,
                               @PathParam("name") String name,
                               @QueryParam("debug") boolean debug,
                               @QueryParam("refresh") boolean refresh) {

        if (!authorized(ctx)) return forbidden();

        final AutoRefreshingReference<Map<String, EntityConfig>> configs = getConfigs();
        if (debug || refresh) {
            log.info("getConfig: refreshing");
            refresh(configs);
        }
        final Map<String, EntityConfig> configMap = configs.get();

        final EntityConfig config;
        synchronized (configMap) {
            config = configMap.get(name.toLowerCase());
        }

        if (debug && config != null) {
            EntityConfig localConfig = null;
            try {
                localConfig = toEntityConfig(forName(config.getClassName()));
            } catch (Exception e) {
                log.warn("getConfig(" + name + "): error loading entity config", e);
            }
            if (localConfig != null) return ok(localConfig);
        }

        return config == null ? notFound(name) : ok(config);
    }

    public class EntityConfigsMap extends AutoRefreshingReference<Map<String, EntityConfig>> {

        @Getter private List<EntityConfigsEntry> entries = new ArrayList<>();

        @Override public Map<String, EntityConfig> refresh() {
            entries = new ArrayList<>();
            final Map<String, EntityConfig> configMap = new HashMap<>();
            final HashSet<Class<?>> classesWithoutConfigs = new HashSet<>();

            for (Class<? extends Identifiable> clazz : new ClasspathScanner<Identifiable>()
                    .setFilter(new AnnotationTypeFilter(ECType.class))
                    .setPackages(getConfiguration().getDatabase().getHibernate().getEntityPackages())
                    .scan().stream()
                    .filter(c -> c.getAnnotation(ECType.class).root())
                    .collect(Collectors.toList())) {

                final EntityConfig config = toEntityConfig(clazz);
                if (config != null) {
                    final String lcClass = clazz.getName().toLowerCase();
                    final String lcSimpleClass = clazz.getSimpleName().toLowerCase();
                    final EntityConfigsEntry entry = new EntityConfigsEntry(config)
                            .addName(lcClass)
                            .addName(clazz.getSimpleName().toLowerCase());
                    entries.add(entry);

                    configMap.put(lcClass, config);
                    if (configMap.containsKey(lcSimpleClass)) {
                        log.warn("config already contains "+lcSimpleClass+", not overwriting with "+clazz.getName());
                    } else {
                        configMap.put(lcSimpleClass, config);
                    }
                } else {
                    classesWithoutConfigs.add(clazz);
                }
            }

            if (classesWithoutConfigs.size() > 0) {
                log.warn("No config(s) found for class(es): " + StringUtil.toString(classesWithoutConfigs));
            }

            synchronized (configs) {
                lastConfig.set(configMap);
                configs.set(configMap);
            }
            return configs.get();
        }

        @Override public long getTimeout() { return getConfigRefreshInterval(); }
    }

    private EntityConfig getEntityConfig(Class<?> clazz) throws Exception { return getEntityConfig(clazz, true); }

    private EntityConfig getEntityConfig(Class<?> clazz, boolean root) throws Exception {
        EntityConfig entityConfig;
        try {
            final InputStream in = loadResourceAsStream(ENTITY_CONFIG_BASE + "/" + packagePath(clazz) + "/" +
                                                        clazz.getSimpleName() + ".json");
            entityConfig = fromJson(in, EntityConfig.class, FULL_MAPPER_ALLOW_COMMENTS);
        } catch (Exception e) {
            log.debug("getEntityConfig(" + clazz.getName() + "): Exception while reading JSON entity config", e);
            entityConfig = new EntityConfig();
        }

        entityConfig.setClassName(clazz.getName());

        try {
            final EntityConfig updated = entityConfig.updateWithAnnotations(clazz, root);

            DAO dao = null;
            try {
                dao = getConfiguration().getDaoForEntityClass(clazz);
            } catch (Exception e) {
                log.info("getEntityConfig: creating EntityConfig for class without a DAO: "+clazz.getName()+": "+shortError(e));
            }

            // add SQL search fields, if the entity supports them
            if (SqlViewSearchResult.class.isAssignableFrom(clazz) && dao instanceof AbstractCRUDDAO) {
                updated.setSqlViewFields(((AbstractCRUDDAO) dao).getSearchFields());
            }

            if (empty(updated.getName())) updated.setName(clazz.getSimpleName());
            return updated;

        } catch (Exception e) {
            log.warn("getEntityConfig(" + clazz.getName() + "): Exception while reading entity cfg annotations", e);
            return null;
        }
    }

    private EntityConfig toEntityConfig(Class<?> clazz) {

        EntityConfig entityConfig = new EntityConfig();
        try {
            entityConfig = getEntityConfig(clazz);
            if (entityConfig == null) return null;

            Class<?> parent = clazz.getSuperclass();
            while (!parent.getName().equals(Object.class.getName())) {
                ECType parentECType = parent.getAnnotation(ECType.class);
                if (parentECType != null && parentECType.root()) {
                    final EntityConfig parentConfig = getEntityConfig(parent);
                    if (parentConfig != null) entityConfig.addParent(parentConfig);
                }
                parent = parent.getSuperclass();
            }

            setNames(entityConfig);
        } catch (Exception e) {
            log.warn("toEntityConfig("+clazz.getName()+"): "+e);
        }

        return entityConfig;
    }

    protected void setNames(EntityConfig config) {
        for (Map.Entry<String, EntityFieldConfig> fieldConfig : config.getFields().entrySet()) {
            fieldConfig.getValue().setName(fieldConfig.getKey());
        }

        if (config.hasChildren()) {
            final Map<String, EntityConfig> children = config.getChildren();
            for (Map.Entry<String, EntityConfig> childConfig : children.entrySet()) {
                final EntityConfig child = childConfig.getValue();
                child.setName(childConfig.getKey());
                setNames(child);
            }
        }
    }

    @NoArgsConstructor @Accessors(chain=true)
    public static class EntityConfigsEntry {
        @Getter @Setter private Set<String> names = new LinkedHashSet<>();
        @Getter @Setter private EntityConfig entityConfig;

        public EntityConfigsEntry(EntityConfig config) { this.entityConfig = config; }

        public EntityConfigsEntry addName(String n) { this.names.add(n); return this; }
    }
}
