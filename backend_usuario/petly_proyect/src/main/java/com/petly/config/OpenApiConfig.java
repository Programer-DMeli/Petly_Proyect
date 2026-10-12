package com.petly.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuración de OpenAPI 3 / Swagger UI para la API de usuarios (Spring Boot).
 */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Petly User API")
                        .description("API para cuentas, perfiles, catálogo y solicitudes del adoptante (PLANNING v2).")
                        .version("0.1.0")
                        .license(new License().name("Academic").url("")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Desarrollo local"),
                        new Server().url("http://10.0.2.2:8080").description("Android Emulator")
                ))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}