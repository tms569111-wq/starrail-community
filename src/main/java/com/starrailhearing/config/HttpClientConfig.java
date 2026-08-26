package com.starrailhearing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class HttpClientConfig {

    @Bean
    RestClient mihomoRestClient(
            RestClient.Builder builder,
            AppProperties properties
    ) {
        return configured(
                builder,
                properties.mihomo().baseUrl().toString(),
                properties.mihomo().userAgent(),
                Duration.ofSeconds(3),
                Duration.ofSeconds(8)
        );
    }

    @Bean
    RestClient enkaRestClient(
            RestClient.Builder builder,
            AppProperties properties
    ) {
        return configured(
                builder,
                properties.enka().baseUrl().toString(),
                properties.enka().userAgent(),
                positive(properties.enka().connectTimeout(), Duration.ofSeconds(3)),
                positive(properties.enka().readTimeout(), Duration.ofSeconds(15))
        );
    }

    private RestClient configured(
            RestClient.Builder builder,
            String baseUrl,
            String userAgent,
            Duration connectTimeout,
            Duration readTimeout
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return builder.clone()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                .requestFactory(requestFactory)
                .build();
    }

    private Duration positive(Duration value, Duration fallback) {
        if (value == null || value.isZero() || value.isNegative()) return fallback;
        return value;
    }
}
