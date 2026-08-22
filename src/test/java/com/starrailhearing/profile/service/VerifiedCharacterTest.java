package com.starrailhearing.profile.service;

import com.starrailhearing.character.domain.GameCharacter;
import com.starrailhearing.profile.domain.GameProfile;
import com.starrailhearing.profile.domain.VerifiedCharacter;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class VerifiedCharacterTest {

    @Test
    void 낮은_성혼을_다시_관측해도_기존_성혼을_내리지_않는다() {
        LocalDateTime first = LocalDateTime.of(2026, 8, 11, 10, 0);
        VerifiedCharacter verified = new VerifiedCharacter(
                mock(GameProfile.class),
                mock(GameCharacter.class),
                4,
                first
        );

        verified.refresh(2, first.plusHours(1));

        assertThat(verified.getEidolon()).isEqualTo(4);
        assertThat(verified.getLastVerifiedAt()).isEqualTo(first.plusHours(1));
    }

    @Test
    void 높은_성혼을_관측하면_최댓값으로_갱신한다() {
        LocalDateTime first = LocalDateTime.of(2026, 8, 11, 10, 0);
        VerifiedCharacter verified = new VerifiedCharacter(
                mock(GameProfile.class),
                mock(GameCharacter.class),
                2,
                first
        );

        verified.refresh(6, first.plusDays(1));

        assertThat(verified.getEidolon()).isEqualTo(6);
    }
}
