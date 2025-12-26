package com.carselling.oldcar.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

/**
 * Configuration for Elasticsearch integration.
 *
 * Uses Spring Data's Elasticsearch client and wires repositories under
 * {@code com.carselling.oldcar.search}. Connection details are driven by
 * elasticsearch.* properties (see application.yml).
 */
@Configuration
@EnableElasticsearchRepositories(basePackages = "com.carselling.oldcar.search")
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
        ClientConfiguration clientConfiguration = ClientConfiguration.builder()
                .connectedTo(elasticsearchHost + ":" + elasticsearchPort)
                .withConnectTimeout(10000)
                .withSocketTimeout(30000)
                .build();

        // Note: authentication and SSL for the new ElC client should be configured
        // via spring.elasticsearch.* or an HttpClient customizer if needed. For now
        // we rely on the simple host/port configuration above.

        return clientConfiguration;
    }
}
