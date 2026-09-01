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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResilientGameProfileClientTest {

    @Test
    void 엔카_장애시_미호모로_폴백하고_짧게_캐시한다() {
        AtomicInteger primaryCalls = new AtomicInteger();
        AtomicInteger fallbackCalls = new AtomicInteger();
        ProfileProviderClient primary = new StubClient(ProfileProvider.ENKA, () -> {
            primaryCalls.incrementAndGet();
            throw new AppException(ErrorCode.UPSTREAM_UNAVAILABLE);
        });
        PublicGameProfile expected = new PublicGameProfile(
                ProfileProvider.MIHOMO, "800000001", "프로필", "", true, List.of()
        );
        ProfileProviderClient fallback = new StubClient(ProfileProvider.MIHOMO, () -> {
            fallbackCalls.incrementAndGet();
            return expected;
        });
        ResilientGameProfileClient client = new ResilientGameProfileClient(
                List.of(fallback, primary), properties(),
                Clock.fixed(Instant.parse("2026-08-18T00:00:00Z"), ZoneOffset.UTC)
        );

        assertThat(client.fetch("800000001", false)).isSameAs(expected);
        assertThat(client.fetch("800000001", false)).isSameAs(expected);
        assertThat(primaryCalls).hasValue(1);
        assertThat(fallbackCalls).hasValue(1);
    }

    @Test
    void 모든_공급자가_UID를_찾지_못하면_조회_실패를_구분한다() {
        ProfileProviderClient primary = new StubClient(ProfileProvider.ENKA, () -> {
            throw new AppException(ErrorCode.PROFILE_LOOKUP_FAILED);
        });
        ProfileProviderClient fallback = new StubClient(ProfileProvider.MIHOMO, () -> {
            throw new AppException(ErrorCode.PROFILE_LOOKUP_FAILED);
        });
        ResilientGameProfileClient client = new ResilientGameProfileClient(
                List.of(fallback, primary), properties(),
                Clock.fixed(Instant.parse("2026-08-18T00:00:00Z"), ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> client.fetch("800000001", false))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.PROFILE_LOOKUP_FAILED)
                );
    }

    @Test
    void 같은_UID의_동시_조회는_외부_공급자를_한_번만_호출한다() throws Exception {
        int workers = 8;
        AtomicInteger providerCalls = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch providerEntered = new CountDownLatch(1);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        PublicGameProfile expected = new PublicGameProfile(
                ProfileProvider.ENKA, "800000001", "프로필", "", true, List.of()
        );
        ProfileProviderClient primary = new StubClient(ProfileProvider.ENKA, () -> {
            providerCalls.incrementAndGet();
            providerEntered.countDown();
            await(releaseProvider);
            return expected;
        });
        ResilientGameProfileClient client = new ResilientGameProfileClient(
                List.of(primary), properties(100, 4, 8, Duration.ofSeconds(3)),
                Clock.fixed(Instant.parse("2026-08-18T00:00:00Z"), ZoneOffset.UTC)
        );
        ExecutorService executor = Executors.newFixedThreadPool(workers);

        try {
            List<Future<PublicGameProfile>> results = new ArrayList<>();
            for (int i = 0; i < workers; i++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return client.fetch("800000001", true);
                }));
            }

            assertThat(ready.await(1, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(providerEntered.await(1, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(100);
            releaseProvider.countDown();

            for (Future<PublicGameProfile> result : results) {
                assertThat(result.get(2, TimeUnit.SECONDS)).isSameAs(expected);
            }
            assertThat(providerCalls).hasValue(1);
        } finally {
            releaseProvider.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void 공급자_전체의_동시_호출_수를_제한한다() throws Exception {
        int workers = 6;
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch twoEntered = new CountDownLatch(2);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        PublicGameProfile expected = new PublicGameProfile(
                ProfileProvider.ENKA, "800000001", "프로필", "", true, List.of()
        );
        ProfileProviderClient primary = new StubClient(ProfileProvider.ENKA, () -> {
            int current = active.incrementAndGet();
            maximumActive.accumulateAndGet(current, Math::max);
            twoEntered.countDown();
            try {
                await(releaseProvider);
                return expected;
            } finally {
                active.decrementAndGet();
            }
        });
        ResilientGameProfileClient client = new ResilientGameProfileClient(
                List.of(primary), properties(100, 2, workers, Duration.ofSeconds(3)),
                Clock.fixed(Instant.parse("2026-08-18T00:00:00Z"), ZoneOffset.UTC)
        );
        ExecutorService executor = Executors.newFixedThreadPool(workers);

        try {
            List<Future<PublicGameProfile>> results = new ArrayList<>();
            for (int i = 0; i < workers; i++) {
                String uid = "80000000" + i;
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return client.fetch(uid, true);
                }));
            }

            assertThat(ready.await(1, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(twoEntered.await(1, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(100);
            assertThat(maximumActive).hasValue(2);
            releaseProvider.countDown();

            for (Future<PublicGameProfile> result : results) {
                assertThat(result.get(3, TimeUnit.SECONDS)).isSameAs(expected);
            }
            assertThat(maximumActive).hasValue(2);
        } finally {
            releaseProvider.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void 공유_UID_캐시는_설정한_최대_개수를_넘지_않는다() {
        AtomicInteger providerCalls = new AtomicInteger();
        PublicGameProfile expected = new PublicGameProfile(
                ProfileProvider.ENKA, "800000001", "프로필", "", true, List.of()
        );
        ProfileProviderClient primary = new StubClient(ProfileProvider.ENKA, () -> {
            providerCalls.incrementAndGet();
            return expected;
        });
        ResilientGameProfileClient client = new ResilientGameProfileClient(
                List.of(primary), properties(2, 2, 2, Duration.ofSeconds(3)),
                Clock.fixed(Instant.parse("2026-08-18T00:00:00Z"), ZoneOffset.UTC)
        );

        client.fetch("800000001", false);
        client.fetch("800000002", false);
        client.fetch("800000003", false);
        client.fetch("800000002", false);
        client.fetch("800000001", false);

        assertThat(providerCalls).hasValue(4);
    }

    private AppProperties properties() {
        return properties(5000, 8, 30, Duration.ofSeconds(15));
    }

    private AppProperties properties(
            int cacheMaxEntries,
            int maxConcurrentRequests,
            int maxWaitingRequests,
            Duration requestWaitTimeout
    ) {
        return new AppProperties(
                new AppProperties.Operator(""),
                new AppProperties.Account(Duration.ofDays(30)),
                new AppProperties.Aggregation(Duration.ofMinutes(3)),
                new AppProperties.TitleVerification("./build/test", 2097152, 3200, Duration.ofDays(30)),
                new AppProperties.Mihomo(URI.create("https://mihomo.invalid"), "test",
                        Duration.ofMinutes(1), Duration.ofMinutes(10), URI.create("https://resource.invalid")),
                new AppProperties.Enka(
                        URI.create("https://enka.invalid"),
                        "test",
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(15),
                        Duration.ofSeconds(60),
                        Duration.ofHours(24),
                        cacheMaxEntries,
                        2,
                        maxConcurrentRequests,
                        maxWaitingRequests,
                        requestWaitTimeout,
                        Duration.ofSeconds(30)
                ),
                new AppProperties.ProfileClient(Duration.ofSeconds(20), 3, Duration.ofSeconds(30))
        );
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                throw new AssertionError("테스트 대기 시간이 초과되었습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
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
