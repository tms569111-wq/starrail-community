package com.starrailhearing.member.service;

import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.common.exception.ErrorCode;
import com.starrailhearing.config.AppProperties;
import com.starrailhearing.evaluation.repository.GameVersionRepository;
import com.starrailhearing.member.domain.MemberAccount;
import com.starrailhearing.member.repository.TitleVerificationRequestRepository;
import com.starrailhearing.moderation.service.AdminAuditService;
import com.starrailhearing.profile.domain.ProfileVerificationStatus;
import com.starrailhearing.profile.repository.GameProfileRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TitleRequestPersistenceServiceTest {

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
                properties("4.5"),
                Clock.systemUTC(),
                mock(GameVersionRepository.class)
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
                properties("4.5"),
                clock,
                mock(GameVersionRepository.class)
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
    void 현재_신청_버전이_아닌_값은_화면을_조작해도_거절한다() {
        TitleVerificationRequestRepository repository =
                mock(TitleVerificationRequestRepository.class);
        GameProfileRepository profileRepository = mock(GameProfileRepository.class);
        MemberService memberService = mock(MemberService.class);
        BadgeService badgeService = mock(BadgeService.class);
        AdminAuditService auditService = mock(AdminAuditService.class);
        AppProperties properties = properties("4.5");
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
                versionRepository
        );

        assertThatThrownBy(() -> service.create(
                7L,
                "4.4",
                new TitleImageStorage.StoredTitleImage("evidence.jpg", "image/jpeg")
        )).isInstanceOfSatisfying(AppException.class, exception -> {
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
            assertThat(exception.getMessage()).contains("4.5");
        });

        verify(versionRepository, never()).findByVersionCode("4.4");
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private AppProperties properties(String platinumVersion) {
        return new AppProperties(
                new AppProperties.Operator("", platinumVersion, "", ""),
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
