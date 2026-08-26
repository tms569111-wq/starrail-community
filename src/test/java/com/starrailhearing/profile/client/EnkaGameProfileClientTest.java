package com.starrailhearing.profile.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
}
