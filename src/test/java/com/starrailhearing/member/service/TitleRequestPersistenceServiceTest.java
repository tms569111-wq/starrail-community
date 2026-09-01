package com.starrailhearing.member.service;

import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.common.exception.ErrorCode;
import com.starrailhearing.config.AppProperties;
import com.starrailhearing.evaluation.domain.GameVersion;
import com.starrailhearing.evaluation.domain.VersionStatus;
import com.starrailhearing.evaluation.repository.GameVersionRepository;
import com.starrailhearing.member.domain.BadgeType;
import com.starrailhearing.member.domain.MemberAccount;
import com.starrailhearing.member.domain.TitleRequestStatus;
import com.starrailhearing.member.repository.TitleVerificationRequestRepository;
import com.starrailhearing.moderation.service.AdminAuditService;
import com.starrailhearing.notification.service.MemberNotificationService;
import com.starrailhearing.profile.domain.ProfileVerificationStatus;
import com.starrailhearing.profile.repository.GameProfileRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TitleRequestPersistenceServiceTest {

    @Test
    void 신청할_수_있는_이상중재_칭호_네_등급을_제공한다() {
        TitleRequestPersistenceService service = service(
                mock(TitleVerificationRequestRepository.class),
                mock(GameProfileRepository.class),
                mock(MemberService.class),
                mock(GameVersionRepository.class)
        );

        assertThat(service.applicationTiers())
                .extracting(TitleRequestPersistenceService.TitleApplicationTierView::label)
                .containsExactly(
                        "이상중재 브론즈",
                        "이상중재 실버",
                        "이상중재 골드",
                        "이상중재 플래티넘"
                );
    }

    @Test
    void 칭호_신청_버전은_공개된_4점5_이후_버전만_보여준다() {
        GameVersionRepository versionRepository = mock(GameVersionRepository.class);
        GameVersion version44 = openVersion("4.4");
        GameVersion version45 = openVersion("4.5");
        GameVersion version410 = openVersion("4.10");
        when(versionRepository.findAllByStatusInOrderByCreatedAtDesc(List.of(
                VersionStatus.OPEN,
                VersionStatus.CLOSING,
                VersionStatus.CLOSED
        ))).thenReturn(List.of(version410, version45, version44));
        TitleRequestPersistenceService service = service(
                mock(TitleVerificationRequestRepository.class),
                mock(GameProfileRepository.class),
                mock(MemberService.class),
                versionRepository
        );

        assertThat(service.applicationVersions())
                .extracting(TitleRequestPersistenceService.TitleApplicationVersionView::versionCode)
                .containsExactly("4.10", "4.5");
    }

    @Test
    void 같은_버전의_신청_슬롯이_있으면_이미지_처리_전에_거절한다() {
        TitleVerificationRequestRepository repository =
                mock(TitleVerificationRequestRepository.class);
        GameProfileRepository profileRepository = mock(GameProfileRepository.class);
        MemberService memberService = mock(MemberService.class);
        GameVersionRepository versionRepository = mock(GameVersionRepository.class);
        when(memberService.requireActiveForWrite(7L)).thenReturn(mock(MemberAccount.class));
        when(profileRepository.existsByMember_IdAndVerificationStatus(
                7L, ProfileVerificationStatus.VERIFIED
        )).thenReturn(true);
        when(versionRepository.findByVersionCode("4.5")).thenReturn(Optional.of(openVersion("4.5")));
        when(repository.existsByMember_IdAndGameVersionAndStatusIn(
                7L,
                "4.5",
                List.of(TitleRequestStatus.PENDING, TitleRequestStatus.CANCELLED)
        )).thenReturn(true);
        TitleRequestPersistenceService service = service(
                repository, profileRepository, memberService, versionRepository
        );

        assertThatThrownBy(() -> service.validateSubmissionBeforeUpload(7L, "4.5", "BRONZE"))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.TITLE_REQUEST_ALREADY_EXISTS));

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 이미_보유한_칭호보다_높은_등급만_다시_신청할_수_있다() {
        TitleVerificationRequestRepository repository =
                mock(TitleVerificationRequestRepository.class);
        GameProfileRepository profileRepository = mock(GameProfileRepository.class);
        MemberService memberService = mock(MemberService.class);
        BadgeService badgeService = mock(BadgeService.class);
        GameVersionRepository versionRepository = mock(GameVersionRepository.class);
        MemberAccount member = mock(MemberAccount.class);
        when(memberService.requireActiveForWrite(7L)).thenReturn(member);
        when(profileRepository.existsByMember_IdAndVerificationStatus(
                7L, ProfileVerificationStatus.VERIFIED
        )).thenReturn(true);
        when(versionRepository.findByVersionCode("4.5")).thenReturn(Optional.of(openVersion("4.5")));
        when(badgeService.find(7L, "4.5")).thenReturn(new BadgeView(
                "4.5", BadgeType.GOLD, BadgeType.GOLD.label(), BadgeType.GOLD.colorHex()
        ));
        TitleRequestPersistenceService service = new TitleRequestPersistenceService(
                repository,
                profileRepository,
                memberService,
                badgeService,
                mock(AdminAuditService.class),
                properties(),
                Clock.systemUTC(),
                versionRepository,
                mock(MemberNotificationService.class)
        );

        assertThatThrownBy(() -> service.validateSubmissionBeforeUpload(7L, "4.5", "SILVER"))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getMessage()).contains("더 높은 등급"));
        assertThat(service.validateSubmissionBeforeUpload(7L, "4.5", "PLATINUM").badgeType())
                .isEqualTo(BadgeType.PLATINUM);
    }

    @Test
    void 계정_화면의_칭호_신청_이력은_최근_20건만_조회한다() {
        TitleVerificationRequestRepository repository =
                mock(TitleVerificationRequestRepository.class);
        MemberService memberService = mock(MemberService.class);
        when(repository.findTop20ByMember_IdOrderByCreatedAtDesc(7L)).thenReturn(List.of());
        TitleRequestPersistenceService service = new TitleRequestPersistenceService(
                repository,
                mock(GameProfileRepository.class),
                memberService,
                mock(BadgeService.class),
                mock(AdminAuditService.class),
                properties(),
                Clock.systemUTC(),
                mock(GameVersionRepository.class),
                mock(MemberNotificationService.class)
        );

        assertThat(service.memberViews(7L)).isEmpty();

        verify(memberService).requireReadable(7L);
        verify(repository).findTop20ByMember_IdOrderByCreatedAtDesc(7L);
    }

    @Test
    void 정리_스케줄은_만료와_삭제대상을_각각_최대_100건만_가져온다() {
        TitleVerificationRequestRepository repository =
                mock(TitleVerificationRequestRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC);
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
        when(repository.findTop100ByStatusAndExpiresAtLessThanEqualOrderByIdAsc(
                com.starrailhearing.member.domain.TitleRequestStatus.PENDING,
                now
        )).thenReturn(List.of());
        when(repository.findTop100ByPrivateImagePathIsNotNullAndStatusNotOrderByIdAsc(
                com.starrailhearing.member.domain.TitleRequestStatus.PENDING
        )).thenReturn(List.of());
        TitleRequestPersistenceService service = new TitleRequestPersistenceService(
                repository,
                mock(GameProfileRepository.class),
                mock(MemberService.class),
                mock(BadgeService.class),
                mock(AdminAuditService.class),
                properties(),
                clock,
                mock(GameVersionRepository.class),
                mock(MemberNotificationService.class)
        );

        assertThat(service.expirePending()).isEmpty();
        assertThat(service.pendingEvidenceCleanup()).isEmpty();

        verify(repository).findTop100ByStatusAndExpiresAtLessThanEqualOrderByIdAsc(
                com.starrailhearing.member.domain.TitleRequestStatus.PENDING,
                now
        );
        verify(repository).findTop100ByPrivateImagePathIsNotNullAndStatusNotOrderByIdAsc(
                com.starrailhearing.member.domain.TitleRequestStatus.PENDING
        );
    }

    @Test
    void 최소_지원_버전_이전_값은_화면을_조작해도_거절한다() {
        TitleVerificationRequestRepository repository =
                mock(TitleVerificationRequestRepository.class);
        GameProfileRepository profileRepository = mock(GameProfileRepository.class);
        MemberService memberService = mock(MemberService.class);
        BadgeService badgeService = mock(BadgeService.class);
        AdminAuditService auditService = mock(AdminAuditService.class);
        AppProperties properties = properties();
        GameVersionRepository versionRepository = mock(GameVersionRepository.class);

        when(memberService.requireActiveForWrite(7L)).thenReturn(mock(MemberAccount.class));
        when(profileRepository.existsByMember_IdAndVerificationStatus(
                7L, ProfileVerificationStatus.VERIFIED
        )).thenReturn(true);

        TitleRequestPersistenceService service = new TitleRequestPersistenceService(
                repository,
                profileRepository,
                memberService,
                badgeService,
                auditService,
                properties,
                Clock.systemUTC(),
                versionRepository,
                mock(MemberNotificationService.class)
        );

        assertThatThrownBy(() -> service.create(
                7L,
                "4.4",
                BadgeType.BRONZE,
                new TitleImageStorage.StoredTitleImage("evidence.jpg", "image/jpeg")
        )).isInstanceOfSatisfying(AppException.class, exception -> {
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
            assertThat(exception.getMessage()).contains("4.5");
        });

        verify(versionRepository, never()).findByVersionCode("4.4");
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private AppProperties properties() {
        return new AppProperties(
                new AppProperties.Operator(""),
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private TitleRequestPersistenceService service(
            TitleVerificationRequestRepository repository,
            GameProfileRepository profileRepository,
            MemberService memberService,
            GameVersionRepository versionRepository
    ) {
        return new TitleRequestPersistenceService(
                repository,
                profileRepository,
                memberService,
                mock(BadgeService.class),
                mock(AdminAuditService.class),
                properties(),
                Clock.systemUTC(),
                versionRepository,
                mock(MemberNotificationService.class)
        );
    }

    private GameVersion openVersion(String versionCode) {
        GameVersion version = new GameVersion(versionCode, 1, "{}");
        version.open("{}", 1, LocalDateTime.of(2026, 8, 27, 0, 0));
        return version;
    }
}
