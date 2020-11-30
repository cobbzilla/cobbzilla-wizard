package org.cobbzilla.wizard.server.config;

import com.github.jknack.handlebars.Handlebars;
import io.swagger.v3.jaxrs2.integration.resources.OpenApiResource;
import io.swagger.v3.oas.integration.SwaggerConfiguration;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.handlebars.HandlebarsUtil;
import org.cobbzilla.util.handlebars.HasHandlebars;
import org.glassfish.jersey.server.ResourceConfig;

import java.util.*;
import java.util.stream.Collectors;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

@Slf4j
public class OpenApiConfiguration {

    // set contactEmail to this value to disable OpenAPI
    public static final String OPENAPI_DISABLED = "openapi_disabled";

    @Getter @Setter private String title;
    @Getter @Setter private String description;
    @Getter @Setter private String contactEmail;
    @Getter @Setter private String terms;
    @Getter @Setter private String licenseName;
    @Getter @Setter private String licenseUrl;

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

        final OpenAPI oas = new OpenAPI();
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

        oas.info(info);
        final List<Server> servers = new ArrayList<>();
        servers.add(new Server().url(configuration.getHttp().getBaseUri()));
        oas.servers(servers);
        final SwaggerConfiguration oasConfig = new SwaggerConfiguration()
                .openAPI(oas)
                .prettyPrint(true)
                .resourcePackages(Arrays.stream(configuration.getJersey().getResourcePackages()).collect(Collectors.toSet()));

        rc.register(new OpenApiResource().openApiConfiguration(oasConfig));
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
