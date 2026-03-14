package com.carselling.oldcar.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;

/**
 * Explicit Data Repository Configuration
 * 
 * This configuration explicitly separates JPA and Redis repositories to avoid
 * the "Could not safely identify store assignment for repository" warning.
 */
@Configuration
@EnableJpaRepositories(basePackages = {"com.carselling.oldcar.repository", "com.carselling.oldcar.scheduler"})
@EnableRedisRepositories(basePackages = "com.carselling.oldcar.redis.repository")
public class DataRepositoryConfig {
    // No explicit beans needed, just the annotations to partition the space
}
