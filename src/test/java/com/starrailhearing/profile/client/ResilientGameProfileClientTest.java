package com.starrailhearing.profile.client;

import com.starrailhearing.character.domain.ProfileProvider;
import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.common.exception.ErrorCode;
import com.starrailhearing.config.AppProperties;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
                ProfileProvider.ENKA, "800000001", "프로필", "", true, List.of(), 120
        );
        ProfileProviderClient fallback = new StubClient(ProfileProvider.ENKA, () -> {
            fallbackCalls.incrementAndGet();
            return expected;
        });
        Clock clock = Clock.fixed(Instant.parse("2026-08-18T00:00:00Z"), ZoneOffset.UTC);
        ResilientGameProfileClient client = client(List.of(primary, fallback), clock);

        assertThat(client.fetch("800000001", false).cacheTtlSeconds()).isEqualTo(120);
        assertThat(client.fetch("800000001", true).cacheTtlSeconds()).isEqualTo(120);
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
        Clock clock = Clock.fixed(Instant.parse("2026-08-18T00:00:00Z"), ZoneOffset.UTC);
        ResilientGameProfileClient client = client(List.of(primary, fallback), clock);

        assertThatThrownBy(() -> client.fetch("800000001", false))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.PROFILE_LOOKUP_FAILED)
                );
    }

    @Test
    void 같은_UID의_동시_요청은_공급자를_한_번만_호출한다() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ProfileProviderClient provider = new StubClient(ProfileProvider.MIHOMO, () -> {
            calls.incrementAndGet();
            try {
                Thread.sleep(50);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(exception);
            }
            return new PublicGameProfile(
                    ProfileProvider.MIHOMO,
                    "800000001",
                    "프로필",
                    "",
                    true,
                    List.of(),
                    120
            );
        });
        ResilientGameProfileClient client = client(
                List.of(provider),
                Clock.fixed(Instant.parse("2026-08-18T00:00:00Z"), ZoneOffset.UTC)
        );
        ExecutorService executor = Executors.newFixedThreadPool(20);
        try {
            List<Future<PublicGameProfile>> futures = java.util.stream.IntStream.range(0, 20)
                    .mapToObj(ignored -> executor.submit(() -> {
                        start.await();
                        return client.fetch("800000001", false);
                    }))
                    .toList();
            start.countDown();
            for (Future<PublicGameProfile> future : futures) {
                assertThat(future.get().uid()).isEqualTo("800000001");
            }
        } finally {
            executor.shutdownNow();
        }
        assertThat(calls).hasValue(1);
    }

    private ResilientGameProfileClient client(
            List<ProfileProviderClient> providers,
            Clock clock
    ) {
        AppProperties properties = properties();
        ProfileResponseCache cache = new ProfileResponseCache(
                properties,
                clock,
                new ObjectMapper(),
                Optional.empty()
        );
        ProfileProviderRequestPacer requestPacer = new ProfileProviderRequestPacer(
                properties,
                Optional.empty()
        );
        return new ResilientGameProfileClient(
                providers,
                properties,
                clock,
                cache,
                requestPacer
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
                new AppProperties.ProfileClient(
                        Duration.ofSeconds(20),
                        Duration.ofHours(24),
                        1000,
                        false,
                        4,
                        Duration.ofMillis(250),
                        Duration.ZERO,
                        Duration.ZERO,
                        3,
                        Duration.ofSeconds(30)
                )
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
