package com.carselling.oldcar.config;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

@Configuration
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "elasticsearch.enabled", havingValue = "true", matchIfMissing = false)
public class OpenSearchConfig {

    @Value("${opensearch.host:localhost}")
    private String host;

    @Value("${opensearch.port:9200}")
    private int port;

    @Value("${opensearch.username:admin}")
    private String username;

    @Value("${opensearch.password:StrongPassword123!}")
    private String password;

    @Value("${opensearch.scheme:https}")
    private String scheme;

    @Bean
    public OpenSearchClient openSearchClient() {
        RestClientBuilder builder = RestClient.builder(new HttpHost(host, port, scheme));

        // Configure Credentials if provided
        if (username != null && !username.isBlank() && password != null && !password.isBlank()) {
            final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials(username, password));

            builder.setHttpClientConfigCallback(httpClientBuilder -> {
                httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                configureSsl(httpClientBuilder); // configures SSL if scheme is https
                return httpClientBuilder;
            });
        } else {
            // No credentials
            builder.setHttpClientConfigCallback(httpClientBuilder -> {
                configureSsl(httpClientBuilder);
                return httpClientBuilder;
            });
        }

        RestClient restClient = builder.build();
        OpenSearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        return new OpenSearchClient(transport);
    }

    private void configureSsl(org.apache.http.impl.nio.client.HttpAsyncClientBuilder httpClientBuilder) {
        if ("https".equalsIgnoreCase(scheme)) {
            try {
                // For dev/self-signed certs
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, new TrustManager[] { new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    }

                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    }

                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                } }, new SecureRandom());
                httpClientBuilder.setSSLContext(sslContext);
                httpClientBuilder.setSSLHostnameVerifier((hostname, session) -> true);
            } catch (Exception e) {
                throw new RuntimeException("Failed to configure SSL", e);
            }
        }
    }
}
