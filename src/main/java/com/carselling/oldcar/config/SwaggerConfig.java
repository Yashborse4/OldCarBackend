package com.carselling.oldcar.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Swagger/OpenAPI 3 Configuration for API Documentation
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(getApiInfo())
                .servers(getServers())
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT token for authentication")));
    }

    private Info getApiInfo() {
        return new Info()
                .title("Car Selling Platform API")
                .description("Comprehensive REST API for the Car Selling Platform - " +
                           "supporting user management, vehicle listings, chat functionality, " +
                           "notifications, file uploads, and admin operations")
                .version("v2.0")
                .contact(new Contact()
                        .name("Car Selling Platform Team")
                        .email("support@carselling.com")
                        .url("https://carselling.com"))
                .license(new License()
                        .name("MIT License")
                        .url("https://opensource.org/licenses/MIT"));
    }

    private List<Server> getServers() {
        return List.of(
                new Server()
                        .url("http://localhost:8080")
                        .description("Development Server"),
                new Server()
                        .url("https://api.carselling.com")
                        .description("Production Server"),
                new Server()
                        .url("https://staging-api.carselling.com")
                        .description("Staging Server")
        );
    }
}
