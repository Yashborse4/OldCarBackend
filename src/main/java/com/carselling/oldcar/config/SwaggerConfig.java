package com.carselling.oldcar.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Swagger/OpenAPI 3 Configuration for API Documentation
 */
@Configuration
@org.springframework.context.annotation.Profile("!prod")
public class SwaggerConfig {

        @Value("${app.swagger.servers.dev-url:http://localhost:9000}")
        private String devServerUrl;

        @Value("${app.swagger.servers.prod-url:https://api.carselling.com}")
        private String prodServerUrl;

        @Value("${app.swagger.servers.staging-url:https://staging-api.carselling.com}")
        private String stagingServerUrl;

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
                                                .url(devServerUrl)
                                                .description("Development Server"),
                                new Server()
                                                .url(prodServerUrl)
                                                .description("Production Server"),
                                new Server()
                                                .url(stagingServerUrl)
                                                .description("Staging Server"));
        }
}
