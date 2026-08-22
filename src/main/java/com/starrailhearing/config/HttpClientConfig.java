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
        return configured(builder, properties.mihomo().baseUrl().toString(),
                properties.mihomo().userAgent());
    }

    @Bean
    RestClient enkaRestClient(
            RestClient.Builder builder,
            AppProperties properties
    ) {
        return configured(builder, properties.enka().baseUrl().toString(),
                properties.enka().userAgent());
    }

    private RestClient configured(RestClient.Builder builder, String baseUrl, String userAgent) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(8));
        return builder.clone()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                .requestFactory(requestFactory)
                .build();
    }
}
