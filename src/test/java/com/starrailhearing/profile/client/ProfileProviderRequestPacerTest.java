package com.starrailhearing.profile.client;

import com.starrailhearing.character.domain.ProfileProvider;
import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.common.exception.ErrorCode;
import com.starrailhearing.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfileProviderRequestPacerTest {

    @Test
    void 공급자_호출_간격을_채우지_못한_요청은_거절한다() {
        ProfileProviderRequestPacer pacer = new ProfileProviderRequestPacer(
                properties(),
                Optional.empty()
        );

        pacer.await(ProfileProvider.ENKA);

        assertThatThrownBy(() -> pacer.await(ProfileProvider.ENKA))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.UPSTREAM_UNAVAILABLE)
                );
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
                        4,
                        Duration.ofMillis(250),
                        Duration.ofSeconds(1),
                        Duration.ZERO,
                        3,
                        Duration.ofSeconds(30)
                )
        );
    }
}
