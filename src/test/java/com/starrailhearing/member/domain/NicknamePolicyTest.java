package com.starrailhearing.member.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NicknamePolicyTest {

    @Test
    void 전각문자와_연속공백을_NFKC로_정규화한다() {
        assertThat(NicknamePolicy.display("  ＡＢＣ   개척자  ")).isEqualTo("ABC 개척자");
        assertThat(NicknamePolicy.key("ＡＢＣ 개척자")).isEqualTo("abc 개척자");
    }

    @Test
    void 구분기호로_우회한_운영자_사칭도_막는다() {
        assertThatThrownBy(() -> NicknamePolicy.display("공 식-관리자"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사칭");
        assertThatThrownBy(() -> NicknamePolicy.display("G-M"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사칭");
    }

    @Test
    void 짧은_예약어가_일반_단어의_일부일_때는_과잉차단하지_않는다() {
        assertThat(NicknamePolicy.display("Sigma 개척자")).isEqualTo("Sigma 개척자");
    }
}
