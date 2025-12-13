package com.carselling.oldcar.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

/**
 * Database Configuration for JPA, connection pooling, and database settings
 * Configures HikariCP connection pool and JPA settings
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.carselling.oldcar.repository")
@EnableTransactionManagement
@Slf4j
public class DatabaseConfig {

    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${spring.datasource.driver-class-name}")
    private String driverClassName;

    /**
     * Primary DataSource configuration with HikariCP connection pool
     */
    @Bean
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    public DataSource dataSource() {
        log.info("Configuring HikariCP DataSource");
        
        HikariConfig hikariConfig = new HikariConfig();
        
        // Basic connection properties
        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);
        hikariConfig.setDriverClassName(driverClassName);
        
        // Connection pool settings
        hikariConfig.setMaximumPoolSize(20); // Maximum number of connections
        hikariConfig.setMinimumIdle(5); // Minimum idle connections
        hikariConfig.setConnectionTimeout(30000); // 30 seconds
        hikariConfig.setIdleTimeout(600000); // 10 minutes
        hikariConfig.setMaxLifetime(1800000); // 30 minutes
        hikariConfig.setLeakDetectionThreshold(60000); // 1 minute
        
        // Connection validation
        hikariConfig.setConnectionTestQuery("SELECT 1");
        hikariConfig.setValidationTimeout(5000); // 5 seconds
        
        // Pool name for identification
        hikariConfig.setPoolName("CarSellingHikariCP");
        
        // PostgreSQL Performance optimizations
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "300");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
        
        // PostgreSQL specific settings
        hikariConfig.addDataSourceProperty("reWriteBatchedInserts", "true");
        hikariConfig.addDataSourceProperty("ApplicationName", "CarSellingApp");
        hikariConfig.addDataSourceProperty("connectTimeout", "10");
        hikariConfig.addDataSourceProperty("socketTimeout", "30");
        hikariConfig.addDataSourceProperty("tcpKeepAlive", "true");
        
        // SSL settings for PostgreSQL (disabled for local development)
        hikariConfig.addDataSourceProperty("sslmode", "disable");
        
        log.info("HikariCP configured with pool size: {} - {}", 
                hikariConfig.getMinimumIdle(), hikariConfig.getMaximumPoolSize());
        
        return new HikariDataSource(hikariConfig);
    }

    /**
     * Development profile specific configuration
     */
    @Configuration
    @Profile("dev")
    @Slf4j
    static class DevDatabaseConfig {
        
        @Bean
        @ConfigurationProperties(prefix = "spring.datasource.hikari.dev")
        public HikariConfig devHikariConfig() {
            log.info("Loading development database configuration");
            HikariConfig config = new HikariConfig();
            
            // Development specific settings
            config.setMaximumPoolSize(10); // Smaller pool for development
            config.setMinimumIdle(2);
            config.setConnectionTimeout(20000);
            config.setLeakDetectionThreshold(30000); // Shorter leak detection for dev
            
            return config;
        }
    }

    /**
     * Production profile specific configuration
     */
    @Configuration
    @Profile("prod")
    @Slf4j
    static class ProdDatabaseConfig {
        
        @Bean
        @ConfigurationProperties(prefix = "spring.datasource.hikari.prod")
        public HikariConfig prodHikariConfig() {
            log.info("Loading production database configuration");
            HikariConfig config = new HikariConfig();
            
            // Production specific settings
            config.setMaximumPoolSize(50); // Larger pool for production
            config.setMinimumIdle(10);
            config.setConnectionTimeout(30000);
            config.setIdleTimeout(300000); // 5 minutes
            config.setMaxLifetime(1200000); // 20 minutes
            config.setLeakDetectionThreshold(120000); // 2 minutes
            
            // Production security
            config.addDataSourceProperty("useSSL", "true");
            config.addDataSourceProperty("requireSSL", "true");
            config.addDataSourceProperty("verifyServerCertificate", "true");
            
            return config;
        }
    }

    /**
     * Test profile specific configuration
     */
    @Configuration
    @Profile("test")
    @Slf4j
    static class TestDatabaseConfig {
        
        @Bean
        @ConfigurationProperties(prefix = "spring.datasource.hikari.test")
        public HikariConfig testHikariConfig() {
            log.info("Loading test database configuration");
            HikariConfig config = new HikariConfig();
            
            // Test specific settings
            config.setMaximumPoolSize(5); // Small pool for testing
            config.setMinimumIdle(1);
            config.setConnectionTimeout(10000);
            config.setIdleTimeout(60000); // 1 minute
            config.setMaxLifetime(300000); // 5 minutes
            
            return config;
        }
    }
}
