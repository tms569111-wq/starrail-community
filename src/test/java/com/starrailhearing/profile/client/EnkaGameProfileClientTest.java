package com.starrailhearing.profile.client;

import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.common.exception.ErrorCode;
import com.starrailhearing.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class EnkaGameProfileClientTest {

    @Test
    void 응답_TTL이_상한보다_크면_상한까지만_캐시한다() {
        long actual = EnkaGameProfileClient.boundedTtlSeconds(
                999_999_999L,
                86_400L
        );

        assertThat(actual).isEqualTo(86_400L);
    }

    @Test
    void 응답_TTL이_0이하면_최소_1초만_캐시한다() {
        long actual = EnkaGameProfileClient.boundedTtlSeconds(
                -1L,
                86_400L
        );

        assertThat(actual).isEqualTo(1L);
    }

    @Test
    void TTL이_남아있으면_강제새로고침이어도_가짜_엔카를_한번만_호출한다() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://enka.invalid");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(
                        ExpectedCount.once(),
                        requestTo("https://enka.invalid/api/hsr/uid/800000001")
                )
                .andRespond(withSuccess(profileJson(3_600), MediaType.APPLICATION_JSON));
        EnkaGameProfileClient client = new EnkaGameProfileClient(
                builder.build(),
                properties(),
                fixedClock()
        );

        PublicGameProfile first = client.fetch("800000001", true);
        PublicGameProfile second = client.fetch("800000001", true);

        assertThat(first.nickname()).isEqualTo("테스트 프로필");
        assertThat(second).isSameAs(first);
        server.verify();
    }

    @Test
    void HTTP_429를_받으면_다음_UID도_즉시_막고_가짜_엔카를_재호출하지_않는다() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://enka.invalid");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(
                        ExpectedCount.once(),
                        requestTo("https://enka.invalid/api/hsr/uid/800000001")
                )
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .header(HttpHeaders.RETRY_AFTER, "120"));
        EnkaGameProfileClient client = new EnkaGameProfileClient(
                builder.build(),
                properties(),
                fixedClock()
        );

        assertThatThrownBy(() -> client.fetch("800000001", true))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.PROFILE_UPSTREAM_THROTTLED));
        assertThatThrownBy(() -> client.fetch("800000002", true))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.PROFILE_UPSTREAM_THROTTLED));
        server.verify();
    }

    private AppProperties properties() {
        return new AppProperties(
                new AppProperties.Operator("", "4.4", "PLATINUM", "#8DE9FF"),
                new AppProperties.Account(Duration.ofDays(30)),
                new AppProperties.Aggregation(Duration.ofMinutes(3)),
                new AppProperties.TitleVerification("./build/test", 716800, 1600, Duration.ofDays(30)),
                new AppProperties.Mihomo(
                        URI.create("https://mihomo.invalid"),
                        "test",
                        Duration.ofMinutes(1),
                        Duration.ofMinutes(10),
                        URI.create("https://resource.invalid")
                ),
                new AppProperties.Enka(
                        URI.create("https://enka.invalid"),
                        "test",
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(15),
                        Duration.ofSeconds(60),
                        Duration.ofHours(24),
                        5000,
                        1000,
                        8,
                        30,
                        Duration.ofSeconds(10),
                        Duration.ofSeconds(30)
                ),
                new AppProperties.ProfileClient(Duration.ofSeconds(20), 3, Duration.ofSeconds(30))
        );
    }

    private Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC);
    }

    private String profileJson(long ttl) {
        return """
                {
                  "ttl": %d,
                  "detailInfo": {
                    "uid": "800000001",
                    "nickname": "테스트 프로필",
                    "signature": "투표!",
                    "isDisplayAvatar": true,
                    "avatarDetailList": []
                  }
                }
                """.formatted(ttl);
    }
}
