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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileVerificationServiceTest {

    private static final long MEMBER_ID = 1L;
    private static final String UID = "826149992";
    private static final Duration SYNC_COOLDOWN = Duration.ofMinutes(3);
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-23T00:00:00Z"),
            ZoneOffset.UTC
    );

    @Test
    void UID_입력_한_번으로_캐릭터까지_등록한다() {
        Fixture fixture = new Fixture();
        PublicGameProfile profile = profile("평범한 소개문");
        ProfileSyncResult expected = new ProfileSyncResult(1, 1, 0, 0);
        when(fixture.persistenceService.reserveLookup(
                eq(MEMBER_ID),
                eq(UID),
                any(LocalDateTime.class),
                eq(SYNC_COOLDOWN)
        )).thenReturn(new ProfileRefreshContext(UID));
        when(fixture.profileClient.fetch(UID, true)).thenReturn(profile);
        when(fixture.persistenceService.completeLookup(
                eq(MEMBER_ID), eq(UID), eq(profile), any(LocalDateTime.class)
        )).thenReturn(expected);

        ProfileSyncResult actual = fixture.service.connect(MEMBER_ID, UID);

        assertThat(actual).isSameAs(expected);
    }

    @Test
    void UID_등록은_빈_소개문도_통과한다() {
        Fixture fixture = new Fixture();
        PublicGameProfile profile = profile("");
        when(fixture.persistenceService.reserveLookup(
                eq(MEMBER_ID), eq(UID), any(LocalDateTime.class), eq(SYNC_COOLDOWN)
        )).thenReturn(new ProfileRefreshContext(UID));
        when(fixture.profileClient.fetch(UID, true)).thenReturn(profile);

        fixture.service.connect(MEMBER_ID, UID);

        verify(fixture.persistenceService).completeLookup(
                eq(MEMBER_ID), eq(UID), eq(profile), any(LocalDateTime.class)
        );
    }

    @Test
    void 인증_완료_후에는_소개문을_바꿔도_캐릭터를_갱신한다() {
        Fixture fixture = new Fixture();
        PublicGameProfile changedProfile = profile("이제는 다른 소개문입니다.");
        ProfileSyncResult expected = new ProfileSyncResult(1, 1, 0, 0);
        when(fixture.persistenceService.reserveRefresh(
                eq(MEMBER_ID),
                any(LocalDateTime.class),
                eq(SYNC_COOLDOWN)
        )).thenReturn(new ProfileRefreshContext(UID));
        when(fixture.profileClient.fetch(UID, true)).thenReturn(changedProfile);
        when(fixture.persistenceService.completeRefresh(
                eq(MEMBER_ID),
                eq(changedProfile),
                any(LocalDateTime.class)
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
