package com.starrailhearing.profile.service;

import com.starrailhearing.character.domain.ProfileProvider;
import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.common.exception.ErrorCode;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileVerificationServiceTest {

    private static final long MEMBER_ID = 1L;
    private static final String UID = "826149992";
    private static final Duration SYNC_COOLDOWN = Duration.ofMinutes(1);
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-23T00:00:00Z"),
            ZoneOffset.UTC
    );

    @Test
    void UID_최초_등록은_소개문에_투표가_포함되면_통과한다() {
        Fixture fixture = new Fixture();
        PublicGameProfile profile = profile("반갑습니다. 투표! 참여 중입니다.");
        ProfileChallengeView expected = new ProfileChallengeView(
                UID,
                profile.nickname(),
                "투표!",
                LocalDateTime.now(CLOCK).plusMinutes(10)
        );
        when(fixture.profileClient.fetch(UID, false)).thenReturn(profile);
        when(fixture.persistenceService.prepare(
                eq(MEMBER_ID),
                eq(UID),
                eq(profile),
                eq("투표!"),
                any(LocalDateTime.class)
        )).thenReturn(expected);

        ProfileChallengeView actual = fixture.service.prepare(MEMBER_ID, UID);

        assertThat(actual).isSameAs(expected);
    }

    @Test
    void UID_최초_등록은_소개문에_투표가_없으면_거절한다() {
        Fixture fixture = new Fixture();
        when(fixture.profileClient.fetch(UID, false)).thenReturn(profile("평범한 소개문"));

        assertThatThrownBy(() -> fixture.service.prepare(MEMBER_ID, UID))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.PROFILE_SIGNATURE_MISMATCH)
                );
        verify(fixture.persistenceService, never()).prepare(
                eq(MEMBER_ID),
                eq(UID),
                any(PublicGameProfile.class),
                any(String.class),
                any(LocalDateTime.class)
        );
    }

    @Test
    void 최종_인증도_소개문에_투표가_포함되면_통과한다() {
        Fixture fixture = new Fixture();
        PublicGameProfile profile = profile("안녕하세요. 투표! 부탁드립니다.");
        ProfileSyncResult expected = new ProfileSyncResult(1, 1, 0, 0);
        when(fixture.persistenceService.verificationContext(
                eq(MEMBER_ID),
                any(LocalDateTime.class)
        )).thenReturn(new ProfileVerificationContext(UID, "투표!"));
        when(fixture.profileClient.fetch(UID, true)).thenReturn(profile);
        when(fixture.persistenceService.completeVerification(
                eq(MEMBER_ID),
                eq("투표!"),
                eq(profile),
                any(LocalDateTime.class)
        )).thenReturn(expected);

        ProfileSyncResult actual = fixture.service.verify(MEMBER_ID);

        assertThat(actual).isSameAs(expected);
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
                new AppProperties.Operator("", "4.4", "PLATINUM", "#8DE9FF"),
                new AppProperties.Account(Duration.ofDays(30)),
                new AppProperties.Aggregation(Duration.ofMinutes(3)),
                new AppProperties.TitleVerification("./build/test", 716800, 1600, Duration.ofDays(30)),
                new AppProperties.Mihomo(
                        URI.create("https://mihomo.invalid"),
                        "test",
                        SYNC_COOLDOWN,
                        Duration.ofMinutes(10),
                        URI.create("https://resource.invalid")
                ),
                new AppProperties.Enka(URI.create("https://enka.invalid"), "test"),
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
