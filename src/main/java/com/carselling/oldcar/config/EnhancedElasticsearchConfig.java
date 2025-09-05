package com.carselling.oldcar.config;

import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

import java.time.Duration;

/**
 * Enhanced Elasticsearch configuration with production-ready settings
 */
@Configuration
@EnableElasticsearchRepositories(basePackages = "com.carselling.oldcar.repository.search")
public class EnhancedElasticsearchConfig extends ElasticsearchConfiguration {

    @Value("${spring.elasticsearch.uris:http://localhost:9200}")
    private String elasticsearchUris;

    @Value("${spring.elasticsearch.username:}")
    private String username;

    @Value("${spring.elasticsearch.password:}")
    private String password;

    @Value("${spring.elasticsearch.connection-timeout:5s}")
    private Duration connectionTimeout;

    @Value("${spring.elasticsearch.socket-timeout:10s}")
    private Duration socketTimeout;

    @Value("${elasticsearch.ssl.enabled:false}")
    private boolean sslEnabled;

    @Override
    public ClientConfiguration clientConfiguration() {
        ClientConfiguration.ClientConfigurationBuilder builder = ClientConfiguration.builder()
                .connectedTo(parseHosts(elasticsearchUris))
                .withConnectTimeout(connectionTimeout)
                .withSocketTimeout(socketTimeout);

        // Add authentication if provided
        if (username != null && !username.trim().isEmpty() && 
            password != null && !password.trim().isEmpty()) {
            builder.withBasicAuth(username, password);
        }

        // Add SSL configuration if enabled
        if (sslEnabled) {
            builder.usingSsl();
        }

        return builder.build();
    }

    @Bean
    @Primary
    public ElasticsearchOperations elasticsearchTemplate() {
        return new ElasticsearchRestTemplate(elasticsearchClient());
    }

    /**
     * Parse Elasticsearch URIs to extract hosts
     */
    private String[] parseHosts(String uris) {
        return uris.replace("http://", "")
                  .replace("https://", "")
                  .split(",");
    }
}
