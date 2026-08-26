package com.starrailhearing.profile.service;

import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.common.exception.ErrorCode;
import com.starrailhearing.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfileFetchBulkheadTest {

    @Test
    void 허용된_동시_조회가_가득_차면_추가_요청을_빠르게_거절한다() throws Exception {
        ProfileFetchBulkhead bulkhead = new ProfileFetchBulkhead(properties());
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(() -> blockingAction(bulkhead, entered, release));
            Future<String> second = executor.submit(() -> blockingAction(bulkhead, entered, release));
            assertThat(entered.await(1, java.util.concurrent.TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> bulkhead.execute(() -> "third"))
                    .isInstanceOfSatisfying(AppException.class, exception ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(ErrorCode.UPSTREAM_UNAVAILABLE)
                    );

            release.countDown();
            assertThat(first.get()).isEqualTo("done");
            assertThat(second.get()).isEqualTo("done");
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private String blockingAction(
            ProfileFetchBulkhead bulkhead,
            CountDownLatch entered,
            CountDownLatch release
    ) {
        return bulkhead.execute(() -> {
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(exception);
            }
            return "done";
        });
    }

    private AppProperties properties() {
        return new AppProperties(
                new AppProperties.Operator("", "4.5", "PLATINUM", "#8DE9FF"),
                new AppProperties.Account(Duration.ofDays(30)),
                new AppProperties.Aggregation(Duration.ofMinutes(3)),
                new AppProperties.TitleVerification("./build/test", 716800, 1600, Duration.ofDays(30)),
                new AppProperties.Mihomo(
                        URI.create("https://mihomo.invalid"),
                        "test",
                        Duration.ofMinutes(3),
                        Duration.ofMinutes(10),
                        URI.create("https://resource.invalid")
                ),
                new AppProperties.Enka(URI.create("https://enka.invalid"), "test"),
                new AppProperties.ProfileClient(
                        Duration.ofSeconds(20),
                        Duration.ofHours(24),
                        1000,
                        false,
                        2,
                        Duration.ofMillis(20),
                        Duration.ZERO,
                        Duration.ZERO,
                        3,
                        Duration.ofSeconds(30)
                )
        );
    }
}
