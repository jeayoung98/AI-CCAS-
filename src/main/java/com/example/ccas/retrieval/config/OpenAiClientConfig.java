package com.example.ccas.retrieval.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({
        EmbeddingProperties.class,
        OpenAiProperties.class,
        RetrievalVersionProperties.class,
        RetrievalSearchProperties.class,
        RetrievalEvaluationProperties.class
})
public class OpenAiClientConfig {

    @Bean
    @Qualifier("openAiRestClient")
    RestClient openAiRestClient(RestClient.Builder builder, OpenAiProperties properties) {
        return builder
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
