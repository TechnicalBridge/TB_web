package com.tbridge.ai.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * La documentacion de ms-ai en Swagger.
 *
 * <p>Un solo grupo, el <b>portal</b>: el asistente no tiene rutas internas. El
 * gateway la publica en {@code /v3/api-docs/ms-ai/portal}.
 */
@Configuration
public class OpenApiConfig {

    public static final String JWT = "jwt";

    @Bean
    public OpenAPI documentacion(@Value("${app.version:1.0.0}") String version) {
        return new OpenAPI()
                .info(new Info()
                        .title("ms-ai · Asistente")
                        .version(version)
                        .description("""
                                El asistente del portal del deudor: responde cuánto debe, a quién y cómo pagar, \
                                con el tono ajustado a su ánimo. Solo lee.""")
                        .contact(new Contact().name("Technical Bridge")))
                .components(new Components()
                        .addSecuritySchemes(JWT, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                                .description("El `token` de la sesion del deudor. ms-ai no lo valida: lo "
                                        + "reenvia a ms-debt, que decide que deudas puede ver.")));
    }

    @Bean
    public GroupedOpenApi portal() {
        return GroupedOpenApi.builder()
                .group("portal")
                .displayName("Portal")
                .pathsToMatch("/api/**")
                .build();
    }
}
