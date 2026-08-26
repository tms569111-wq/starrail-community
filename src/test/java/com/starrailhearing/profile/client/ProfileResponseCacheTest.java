package com.starrailhearing.profile.client;

import com.starrailhearing.character.domain.ProfileProvider;
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

import static org.assertj.core.api.Assertions.assertThat;

class ProfileResponseCacheTest {

    @Test
    void 로컬_대체_캐시는_설정된_최대_개수를_넘기지_않는다() {
        ProfileResponseCache cache = new ProfileResponseCache(
                properties(1),
                Clock.fixed(Instant.parse("2026-08-18T00:00:00Z"), ZoneOffset.UTC),
                new ObjectMapper(),
                Optional.empty()
        );
        PublicGameProfile first = profile("800000001");
        PublicGameProfile second = profile("800000002");

        cache.put(first.uid(), first, Duration.ofMinutes(10));
        cache.put(second.uid(), second, Duration.ofMinutes(10));

        assertThat(cache.get(first.uid())).isEmpty();
        assertThat(cache.get(second.uid())).isPresent();
    }

    private PublicGameProfile profile(String uid) {
        return new PublicGameProfile(
                ProfileProvider.ENKA,
                uid,
                "프로필",
                "",
                true,
                List.of(),
                600
        );
    }

    private AppProperties properties(int maximumEntries) {
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
                        maximumEntries,
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
}
