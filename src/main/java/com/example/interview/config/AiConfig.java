package com.example.interview.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({ AiProperties.class, LocalAiProperties.class })
public class AiConfig {

    @Bean
    public RestClient aiRestClient(AiProperties props) {
        return restClient(props.baseUrl(), props.timeoutSeconds());
    }

    @Bean
    public RestClient localAiRestClient(LocalAiProperties props) {
        return restClient(props.baseUrl(), props.timeoutSeconds());
    }

    private static RestClient restClient(String baseUrl, int timeoutSeconds) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}
