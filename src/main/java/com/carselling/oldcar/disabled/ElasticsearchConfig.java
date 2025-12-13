package com.carselling.oldcar.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

/**
 * Configuration for Elasticsearch integration
 */
@Configuration
@EnableElasticsearchRepositories(basePackages = "com.carselling.oldcar.repository.search")
public class ElasticsearchConfig extends ElasticsearchConfiguration {

    @Value("${elasticsearch.host:localhost}")
    private String elasticsearchHost;

    @Value("${elasticsearch.port:9200}")
    private int elasticsearchPort;

    @Value("${elasticsearch.username:}")
    private String username;

    @Value("${elasticsearch.password:}")
    private String password;

    @Value("${elasticsearch.scheme:http}")
    private String scheme;

    @Override
    public ClientConfiguration clientConfiguration() {
        ClientConfiguration.Builder builder = ClientConfiguration.builder()
                .connectedTo(elasticsearchHost + ":" + elasticsearchPort)
                .withConnectTimeout(10000)
                .withSocketTimeout(30000);

        // Add authentication if provided
        if (!username.isEmpty() && !password.isEmpty()) {
            builder.withBasicAuth(username, password);
        }

        // Use HTTPS if specified
        if ("https".equals(scheme)) {
            builder.usingSsl();
        }

        return builder.build();
    }
}
