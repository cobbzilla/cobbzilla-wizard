package org.cobbzilla.wizard.server.config;

import com.github.jknack.handlebars.Handlebars;
import io.swagger.v3.jaxrs2.integration.resources.OpenApiResource;
import io.swagger.v3.oas.integration.SwaggerConfiguration;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.collection.MapBuilder;
import org.cobbzilla.util.handlebars.HandlebarsUtil;
import org.cobbzilla.util.handlebars.HasHandlebars;
import org.cobbzilla.wizard.filters.auth.AuthFilter;
import org.cobbzilla.util.reflect.OpenApiSchema;
import org.cobbzilla.wizard.model.entityconfig.EntityConfig;
import org.cobbzilla.wizard.model.entityconfig.EntityConfigSource;
import org.cobbzilla.wizard.util.ClasspathScanner;
import org.glassfish.jersey.server.ResourceConfig;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.util.*;

import static org.cobbzilla.util.daemon.ZillaRuntime.*;

@Slf4j
public class OpenApiConfiguration {

    // set contactEmail to this value to disable OpenAPI
    public static final String OPENAPI_DISABLED = "openapi_disabled";
    public static final String SEC_API_KEY = "apiKey";
    public static final String API_TAG_UTILITY = "utility";

    @Getter @Setter private String title;
    @Getter @Setter private String description;
    @Getter @Setter private String contactEmail;
    @Getter @Setter private String terms;
    @Getter @Setter private String licenseName;
    @Getter @Setter private String licenseUrl;
    @Getter @Setter private String[] additionalPackages;

    public boolean valid() {
        return !empty(contactEmail) && !contactEmail.equalsIgnoreCase(OPENAPI_DISABLED)
                && !empty(terms) && !empty(licenseName) && !empty(licenseUrl);
    }

    public String title(RestServerConfiguration configuration) {
        return empty(this.title) ? configuration.getServerName() : this.title;
    }

    public void register(RestServerConfiguration configuration, ResourceConfig rc) {
        if (!valid()) {
            log.warn("register: config not valid, not registering OpenApiResource");
            return;
        }

        final Handlebars handlebars;
        final Map<String, Object> ctx = new HashMap<>();
        if (configuration instanceof HasHandlebars) {
            handlebars = ((HasHandlebars) configuration).getHandlebars();
            ctx.put("configuration", configuration);
        } else {
            handlebars = null;
        }

        final Info info = new Info()
                .title(subst(title(configuration), handlebars, ctx, configuration))
                .description(subst((empty(description) ? title(configuration) : description), handlebars, ctx, configuration))
                .termsOfService(subst(terms, handlebars, ctx, configuration))
                .contact(new Contact()
                        .email(subst(contactEmail, handlebars, ctx, configuration)))
                .license(new License()
                        .name(subst(licenseName, handlebars, ctx, configuration))
                        .url(subst(licenseUrl, handlebars, ctx, configuration)))
                .version((configuration.hasVersion() ? configuration.getVersion() : "(configuration.version was missing or empty)"));

        final List<Server> servers = new ArrayList<>();
        servers.add(new Server()
                .url(configuration.getHttp().getBaseUri()));

        final AuthFilter authFilter = configuration.getBean(AuthFilter.class);
        final SecurityScheme securityScheme = new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .name(authFilter.getAuthTokenHeader())
                .in(SecurityScheme.In.HEADER);

        final OpenAPI oas = new OpenAPI()
                .components(new Components().securitySchemes(MapBuilder.build(SEC_API_KEY, securityScheme)))
                .info(info)
                .tags(configuration.getOpenApiTags())
                .servers(servers);

        final Set<String> packages = getPackages(configuration);
        if (configuration instanceof HasDatabaseConfiguration) {
            try {
                addEntitySchemas(oas, packages.toArray(String[]::new), configuration);
            } catch (Exception e) {
                log.warn("register: error reading entity configs or converting to OpenApi schemas: "+shortError(e));
            }
        }

        final SwaggerConfiguration oasConfig = new SwaggerConfiguration()
                .openAPI(oas)
                .prettyPrint(true)
                .resourcePackages(packages);

        rc.register(new OpenApiResource().openApiConfiguration(oasConfig));
    }

    public static final AnnotationTypeFilter SCHEMA_FILTER = new AnnotationTypeFilter(OpenApiSchema.class);

    protected void addEntitySchemas(OpenAPI oas, String[] packages, RestServerConfiguration configuration) throws Exception {
        final EntityConfigSource entityConfigSource = configuration.getBean(EntityConfigSource.class);
        final Set<Class<?>> apiEntities = new HashSet<>();
        final PgRestServerConfiguration pgConfig = (PgRestServerConfiguration) configuration;
        apiEntities.addAll(pgConfig.getEntityClassesReverse());
        apiEntities.addAll(new ClasspathScanner<>()
                .setPackages(packages)
                .setFilter(SCHEMA_FILTER)
                .scan());
        for (Class<?> entity : apiEntities) {
            final OpenApiSchema schema = entity.getAnnotation(OpenApiSchema.class);
            final EntityConfig entityConfig = entityConfigSource.getOrCreateEntityConfig(entity, schema);
            final Schema<Object> s = entityConfig.openApiSchema();
            oas.schema(s.getName(), s);
        }
    }

    protected Set<String> getPackages(RestServerConfiguration configuration) {
        // always add jersey resources
        final Set<String> packages
                = new HashSet<>(Arrays.asList(configuration.getJersey().getResourcePackages()));

        // add entities if we have them
        if (configuration instanceof HasDatabaseConfiguration) {
            final DatabaseConfiguration db = ((HasDatabaseConfiguration) configuration).getDatabase();
            packages.addAll(Arrays.asList(db.getHibernate().getEntityPackages()));
        }
        if (!empty(additionalPackages)) {
            packages.addAll(Arrays.asList(additionalPackages));
        }
        return packages;
    }

    public String subst (String value,
                         Handlebars handlebars,
                         Map<String, Object> ctx,
                         RestServerConfiguration configuration) {
        if (!(value.contains("<<") && value.contains(">>"))) return value;
        if (handlebars == null) return die("subst: value contained <<...>> but configuration does not support Handlebars: "+configuration.getClass().getSimpleName());
        return HandlebarsUtil.apply(handlebars, value, ctx, '<', '>');
    }

}
