package com.starrailhearing.profile.service;

import com.starrailhearing.character.domain.ProfileProvider;
import com.starrailhearing.config.AppProperties;
import com.starrailhearing.member.service.MemberService;
import com.starrailhearing.profile.client.GameProfileClient;
import com.starrailhearing.profile.client.PublicCharacter;
import com.starrailhearing.profile.client.PublicGameProfile;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileVerificationServiceTest {

    private static final long MEMBER_ID = 1L;
    private static final String UID = "826149992";
    private static final String VERIFICATION_STATE_TOKEN = "PROFILE_VERIFICATION";
    private static final Duration SYNC_COOLDOWN = Duration.ofMinutes(3);
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-23T00:00:00Z"),
            ZoneOffset.UTC
    );

    @Test
    void UID_입력_한_번으로_소개문과_무관하게_캐릭터까지_인증한다() {
        Fixture fixture = new Fixture();
        PublicGameProfile profile = profile("평범한 소개문");
        ProfileSyncResult expected = new ProfileSyncResult(1, 1, 0, 0);
        when(fixture.profileClient.fetch(UID, false)).thenReturn(profile);
        when(fixture.persistenceService.bindAndVerify(
                eq(MEMBER_ID),
                eq(UID),
                eq(profile),
                any(LocalDateTime.class)
        )).thenReturn(expected);

        ProfileSyncResult actual = fixture.service.verifyUid(MEMBER_ID, UID);

        assertThat(actual).isSameAs(expected);
        verify(fixture.memberService).reserveProfileFetch(MEMBER_ID, SYNC_COOLDOWN);
    }

    @Test
    void UID_최초_등록은_빈_소개문도_통과한다() {
        Fixture fixture = new Fixture();
        PublicGameProfile profile = profile("");
        when(fixture.profileClient.fetch(UID, false)).thenReturn(profile);

        fixture.service.verifyUid(MEMBER_ID, UID);

        verify(fixture.persistenceService).bindAndVerify(
                eq(MEMBER_ID),
                eq(UID),
                eq(profile),
                any(LocalDateTime.class)
        );
    }

    @Test
    void 잘못된_UID는_외부_조회_제한을_소비하지_않는다() {
        Fixture fixture = new Fixture();

        assertThatThrownBy(() -> fixture.service.verifyUid(MEMBER_ID, "123"))
                .isInstanceOf(com.starrailhearing.common.exception.AppException.class)
                .hasMessageContaining("숫자 9자리");

        verify(fixture.memberService, never()).reserveProfileFetch(anyLong(), any(Duration.class));
        verify(fixture.profileClient, never()).fetch(anyString(), anyBoolean());
    }

    @Test
    void 최종_인증도_소개문과_무관하게_통과한다() {
        Fixture fixture = new Fixture();
        PublicGameProfile profile = profile("원래 사용하던 소개문");
        ProfileSyncResult expected = new ProfileSyncResult(1, 1, 0, 0);
        when(fixture.persistenceService.verificationContext(
                eq(MEMBER_ID),
                any(LocalDateTime.class)
        )).thenReturn(new ProfileVerificationContext(UID, VERIFICATION_STATE_TOKEN));
        when(fixture.profileClient.fetch(UID, true)).thenReturn(profile);
        when(fixture.persistenceService.completeVerification(
                eq(MEMBER_ID),
                eq(VERIFICATION_STATE_TOKEN),
                eq(profile),
                any(LocalDateTime.class)
        )).thenReturn(expected);

        ProfileSyncResult actual = fixture.service.verify(MEMBER_ID);

        assertThat(actual).isSameAs(expected);
        verify(fixture.memberService).reserveProfileFetch(MEMBER_ID, SYNC_COOLDOWN);
    }

    @Test
    void 인증_완료_후에는_소개문을_바꿔도_캐릭터를_갱신한다() {
        Fixture fixture = new Fixture();
        PublicGameProfile changedProfile = profile("이제는 다른 소개문입니다.");
        ProfileSyncResult expected = new ProfileSyncResult(1, 1, 0, 0);
        when(fixture.persistenceService.refreshContext(
                eq(MEMBER_ID),
                any(LocalDateTime.class),
                eq(SYNC_COOLDOWN)
        )).thenReturn(new ProfileRefreshContext(UID));
        when(fixture.profileClient.fetch(UID, true)).thenReturn(changedProfile);
        when(fixture.persistenceService.completeRefresh(
                eq(MEMBER_ID),
                eq(changedProfile),
                any(LocalDateTime.class),
                eq(SYNC_COOLDOWN)
        )).thenReturn(expected);

        ProfileSyncResult actual = fixture.service.refresh(MEMBER_ID);

        assertThat(actual).isSameAs(expected);
        verify(fixture.memberService).reserveProfileFetch(MEMBER_ID, SYNC_COOLDOWN);
    }

    private static PublicGameProfile profile(String signature) {
        return new PublicGameProfile(
                ProfileProvider.ENKA,
                UID,
                "아르카",
                signature,
                true,
                List.of(new PublicCharacter("1001", "테스트 캐릭터", 0))
        );
    }

    private static AppProperties properties() {
        return new AppProperties(
                new AppProperties.Operator(""),
                new AppProperties.Account(Duration.ofDays(30)),
                new AppProperties.Aggregation(Duration.ofMinutes(3)),
                new AppProperties.TitleVerification("./build/test", 2097152, 3200, Duration.ofDays(30)),
                new AppProperties.Mihomo(
                        URI.create("https://mihomo.invalid"),
                        "test",
                        SYNC_COOLDOWN,
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
                        2,
                        8,
                        30,
                        Duration.ofSeconds(15),
                        Duration.ofSeconds(30)
                ),
                new AppProperties.ProfileClient(Duration.ofSeconds(20), 3, Duration.ofSeconds(30))
        );
    }

    private static final class Fixture {
        private final ProfilePersistenceService persistenceService = mock(ProfilePersistenceService.class);
        private final MemberService memberService = mock(MemberService.class);
        private final GameProfileClient profileClient = mock(GameProfileClient.class);
        private final ProfileVerificationService service = new ProfileVerificationService(
                persistenceService,
                memberService,
                profileClient,
                properties(),
                CLOCK
        );
    }
}
