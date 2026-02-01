package com.carselling.oldcar.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Note: In newer Spring GraphQL versions (2.7+ / 3.0+), simply creating the DataLoader bean (as we did in UserDataLoader) 
// using generic BatchLoaderRegistry injection in constructor is enough.
// However, if we need explicit configuration or custom execution strategies, we do it here.
// Since UserDataLoader is a @Component and injects BatchLoaderRegistry, it will self-register on startup.
// This config class ensures we have a place for global GraphQL settings if needed.

@Configuration
public class GraphQlConfig {

    // Prevent recursive or deeply nested queries (DoS protection)
    @Bean
    public graphql.analysis.MaxQueryDepthInstrumentation maxQueryDepthInstrumentation() {
        return new graphql.analysis.MaxQueryDepthInstrumentation(5); // Limit depth to 5
    }

    // Prevent complex queries that consume too many resources (CPU/DB)
    @Bean
    public graphql.analysis.MaxQueryComplexityInstrumentation maxQueryComplexityInstrumentation() {
        // Complexity calculation: Default field cost check is 1.
        // A complexity of 150 allows reasonably rich queries but stops abusive ones.
        return new graphql.analysis.MaxQueryComplexityInstrumentation(150);
    }

    // We can add global interceptors or scalar registrations here
}
