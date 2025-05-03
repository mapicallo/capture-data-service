package com.mapicallo.capture_data_service.infrastructure.config;

import org.opensearch.client.RestClient;
import org.opensearch.client.RestHighLevelClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenSearchConfig {

    private static final String OPENSEARCH_HOST = "localhost"; // Cambia si usas otra IP
    private static final int OPENSEARCH_PORT = 9200;

    @Bean
    public RestHighLevelClient restHighLevelClient() {
        return new RestHighLevelClient(
                RestClient.builder(new org.apache.http.HttpHost(OPENSEARCH_HOST, OPENSEARCH_PORT, "http"))
        );
    }
}
