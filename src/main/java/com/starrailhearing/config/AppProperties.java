package com.starrailhearing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Operator operator,
        Account account,
        Aggregation aggregation,
        TitleVerification titleVerification,
        Mihomo mihomo,
        Enka enka,
        ProfileClient profileClient
) {
    public record Operator(
            String subject,
            String platinumVersion,
            String platinumLabel,
            String platinumColor
    ) {
    }

    public record Account(Duration nicknameCooldown) {
    }

    public record Aggregation(Duration interval) {
    }

    public record TitleVerification(
            String privateUploadDir,
            long maximumBytes,
            int maximumDimension,
            Duration pendingTtl
    ) {
    }

    public record Mihomo(
            URI baseUrl,
            String userAgent,
            Duration syncCooldown,
            Duration challengeTtl,
            URI resourceBaseUrl
    ) {
    }

    public record Enka(
            URI baseUrl,
            String userAgent,
            Duration cacheDefaultTtl,
            int maxRequestsPerSecond,
            int maxConcurrentRequests,
            int maxWaitingRequests,
            Duration requestWaitTimeout,
            Duration backoffOn429
    ) {
    }

    public record ProfileClient(
            Duration cacheTtl,
            int circuitFailureThreshold,
            Duration circuitOpenDuration
    ) {
    }
}
