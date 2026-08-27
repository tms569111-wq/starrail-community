package com.starrailhearing.profile.client;

import com.starrailhearing.character.domain.ProfileProvider;
import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.common.exception.ErrorCode;
import com.starrailhearing.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

class ResilientGameProfileClientLoadTest {

    private static final int REQUESTS = 200;
    private static final String SHARED_UID = "800000001";

    @Test
    @Timeout(15)
    void 같은_UID_이백개는_외부호출_한번으로_합쳐지고_모두_성공한다() throws Exception {
        AtomicInteger providerCalls = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();
        CountDownLatch providerEntered = new CountDownLatch(1);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        PublicGameProfile expected = profile(SHARED_UID);
        ProfileProviderClient provider = new StubClient(ProfileProvider.ENKA, uid -> {
            providerCalls.incrementAndGet();
            providerEntered.countDown();
            await(releaseProvider, Duration.ofSeconds(5));
            return expected;
        });
        ResilientGameProfileClient client = client(provider, 8, 30);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch ready = new CountDownLatch(REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch beganFetch = new CountDownLatch(REQUESTS);

        try {
            List<Future<Outcome>> results = new ArrayList<>();
            for (int i = 0; i < REQUESTS; i++) {
                results.add(executor.submit(() -> fetch(
                        client, SHARED_UID, ready, start, beganFetch, completed
                )));
            }

            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(beganFetch.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(providerEntered.await(3, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(300);

            // 공급자 한 건이 막혀 있는 동안 같은 UID 요청이 먼저 거절되면 안 된다.
            assertThat(completed).hasValue(0);
            releaseProvider.countDown();

            assertThat(outcomes(results)).containsOnly(Outcome.SUCCESS);
            assertThat(providerCalls).hasValue(1);
            assertThat(inFlightSize(client)).isZero();
        } finally {
            releaseProvider.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @Timeout(15)
    void 서로다른_UID_이백개는_동시여덟개와_대기서른개만_받는다() throws Exception {
        AtomicInteger providerCalls = new AtomicInteger();
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();
        CountDownLatch eightEntered = new CountDownLatch(8);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        ProfileProviderClient provider = new StubClient(ProfileProvider.ENKA, uid -> {
            providerCalls.incrementAndGet();
            int current = active.incrementAndGet();
            maximumActive.accumulateAndGet(current, Math::max);
            eightEntered.countDown();
            try {
                await(releaseProvider, Duration.ofSeconds(5));
                return profile(uid);
            } finally {
                active.decrementAndGet();
            }
        });
        ResilientGameProfileClient client = client(provider, 8, 30);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch ready = new CountDownLatch(REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch beganFetch = new CountDownLatch(REQUESTS);

        try {
            List<Future<Outcome>> results = new ArrayList<>();
            for (int i = 0; i < REQUESTS; i++) {
                String uid = String.format("8%08d", i);
                results.add(executor.submit(() -> fetch(
                        client, uid, ready, start, beganFetch, completed
                )));
            }

            assertThat(ready.await(3, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(beganFetch.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(eightEntered.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(waitUntil(() -> completed.get() >= 162, Duration.ofSeconds(3))).isTrue();
            assertThat(completed).hasValue(162);
            assertThat(maximumActive).hasValue(8);

            releaseProvider.countDown();
            List<Outcome> outcomes = outcomes(results);

            assertThat(outcomes.stream().filter(outcome -> outcome == Outcome.SUCCESS).count()).isEqualTo(38);
            assertThat(outcomes.stream().filter(outcome -> outcome == Outcome.THROTTLED).count()).isEqualTo(162);
            assertThat(outcomes).doesNotContain(Outcome.OTHER_ERROR);
            assertThat(providerCalls).hasValue(38);
            assertThat(maximumActive).hasValue(8);
            assertThat(inFlightSize(client)).isZero();
        } finally {
            releaseProvider.countDown();
            executor.shutdownNow();
        }
    }

    private Outcome fetch(
            ResilientGameProfileClient client,
            String uid,
            CountDownLatch ready,
            CountDownLatch start,
            CountDownLatch beganFetch,
            AtomicInteger completed
    ) {
        ready.countDown();
        await(start, Duration.ofSeconds(5));
        beganFetch.countDown();
        try {
            client.fetch(uid, true);
            return Outcome.SUCCESS;
        } catch (AppException exception) {
            return exception.getErrorCode() == ErrorCode.PROFILE_REQUEST_THROTTLED
                    ? Outcome.THROTTLED
                    : Outcome.OTHER_ERROR;
        } finally {
            completed.incrementAndGet();
        }
    }

    private List<Outcome> outcomes(List<Future<Outcome>> futures) throws Exception {
        List<Outcome> outcomes = new ArrayList<>();
        for (Future<Outcome> future : futures) {
            outcomes.add(future.get(8, TimeUnit.SECONDS));
        }
        return outcomes;
    }

    private ResilientGameProfileClient client(
            ProfileProviderClient provider,
            int maxConcurrentRequests,
            int maxWaitingRequests
    ) {
        return new ResilientGameProfileClient(
                List.of(provider),
                properties(maxConcurrentRequests, maxWaitingRequests),
                Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    private AppProperties properties(int maxConcurrentRequests, int maxWaitingRequests) {
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
                        maxConcurrentRequests,
                        maxWaitingRequests,
                        Duration.ofSeconds(10),
                        Duration.ofSeconds(30)
                ),
                new AppProperties.ProfileClient(Duration.ofSeconds(20), 3, Duration.ofSeconds(30))
        );
    }

    private PublicGameProfile profile(String uid) {
        return new PublicGameProfile(ProfileProvider.ENKA, uid, "테스트", "", true, List.of());
    }

    @SuppressWarnings("unchecked")
    private int inFlightSize(ResilientGameProfileClient client) {
        Map<String, ?> inFlight = (Map<String, ?>) ReflectionTestUtils.getField(client, "inFlight");
        return inFlight == null ? -1 : inFlight.size();
    }

    private boolean waitUntil(BooleanSupplier condition, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return true;
            try {
                Thread.sleep(10);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return condition.getAsBoolean();
    }

    private static void await(CountDownLatch latch, Duration timeout) {
        try {
            if (!latch.await(timeout.toNanos(), TimeUnit.NANOSECONDS)) {
                throw new AssertionError("테스트 대기 시간이 초과되었습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private enum Outcome {
        SUCCESS,
        THROTTLED,
        OTHER_ERROR
    }

    private record StubClient(
            ProfileProvider provider,
            java.util.function.Function<String, PublicGameProfile> response
    ) implements ProfileProviderClient {
        @Override
        public PublicGameProfile fetch(String uid, boolean forceUpdate) {
            return response.apply(uid);
        }
    }
}
