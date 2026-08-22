package com.starrailhearing.profile.client;

import com.starrailhearing.character.domain.ProfileProvider;
import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.common.exception.ErrorCode;
import com.starrailhearing.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResilientGameProfileClientTest {

    @Test
    void 주_공급자_장애시_보조_공급자로_폴백하고_짧게_캐시한다() {
        AtomicInteger primaryCalls = new AtomicInteger();
        AtomicInteger fallbackCalls = new AtomicInteger();
        ProfileProviderClient primary = new StubClient(ProfileProvider.MIHOMO, () -> {
            primaryCalls.incrementAndGet();
            throw new AppException(ErrorCode.UPSTREAM_UNAVAILABLE);
        });
        PublicGameProfile expected = new PublicGameProfile(
                ProfileProvider.ENKA, "800000001", "프로필", "", true, List.of()
        );
        ProfileProviderClient fallback = new StubClient(ProfileProvider.ENKA, () -> {
            fallbackCalls.incrementAndGet();
            return expected;
        });
        ResilientGameProfileClient client = new ResilientGameProfileClient(
                List.of(primary, fallback), properties(),
                Clock.fixed(Instant.parse("2026-08-18T00:00:00Z"), ZoneOffset.UTC)
        );

        assertThat(client.fetch("800000001", false)).isSameAs(expected);
        assertThat(client.fetch("800000001", false)).isSameAs(expected);
        assertThat(primaryCalls).hasValue(1);
        assertThat(fallbackCalls).hasValue(1);
    }

    @Test
    void 모든_공급자가_UID를_찾지_못하면_조회_실패를_구분한다() {
        ProfileProviderClient primary = new StubClient(ProfileProvider.MIHOMO, () -> {
            throw new AppException(ErrorCode.PROFILE_LOOKUP_FAILED);
        });
        ProfileProviderClient fallback = new StubClient(ProfileProvider.ENKA, () -> {
            throw new AppException(ErrorCode.PROFILE_LOOKUP_FAILED);
        });
        ResilientGameProfileClient client = new ResilientGameProfileClient(
                List.of(primary, fallback), properties(),
                Clock.fixed(Instant.parse("2026-08-18T00:00:00Z"), ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> client.fetch("800000001", false))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.PROFILE_LOOKUP_FAILED)
                );
    }

    private AppProperties properties() {
        return new AppProperties(
                new AppProperties.Operator("", "4.4", "PLATINUM", "#8DE9FF"),
                new AppProperties.Account(Duration.ofDays(30)),
                new AppProperties.Aggregation(Duration.ofMinutes(3)),
                new AppProperties.TitleVerification("./build/test", 716800, 1600, Duration.ofDays(30)),
                new AppProperties.Mihomo(URI.create("https://mihomo.invalid"), "test",
                        Duration.ofMinutes(1), Duration.ofMinutes(10), URI.create("https://resource.invalid")),
                new AppProperties.Enka(URI.create("https://enka.invalid"), "test"),
                new AppProperties.ProfileClient(Duration.ofSeconds(20), 3, Duration.ofSeconds(30))
        );
    }

    private record StubClient(
            ProfileProvider provider,
            java.util.function.Supplier<PublicGameProfile> response
    ) implements ProfileProviderClient {
        @Override
        public PublicGameProfile fetch(String uid, boolean forceUpdate) {
            return response.get();
        }
    }
}
