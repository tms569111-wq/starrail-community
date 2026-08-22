package com.starrailhearing.vote.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EidolonFilterTest {

    @Test
    void 삼돌부터_오돌까지는_하나의_화면_구간으로_묶는다() {
        EidolonFilter filter = EidolonFilter.E3_TO_E5;

        assertThat(filter.getMinimum()).isEqualTo(3);
        assertThat(filter.getMaximum()).isEqualTo(5);
    }

    @Test
    void 알_수_없는_필터는_전체로_안전하게_처리한다() {
        assertThat(EidolonFilter.from("unknown")).isEqualTo(EidolonFilter.ALL);
    }

    @Test
    void 실제_성혼은_결과_탭의_정확한_구간으로_변환한다() {
        assertThat(EidolonFilter.forEidolon(0)).isEqualTo(EidolonFilter.E0);
        assertThat(EidolonFilter.forEidolon(2)).isEqualTo(EidolonFilter.E2);
        assertThat(EidolonFilter.forEidolon(4)).isEqualTo(EidolonFilter.E3_TO_E5);
        assertThat(EidolonFilter.forEidolon(6)).isEqualTo(EidolonFilter.E6);
    }
}
