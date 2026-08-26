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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TitleRequestPersistenceServiceTest {

    @Test
    void 현재_신청_버전이_아닌_값은_화면을_조작해도_거절한다() {
        TitleVerificationRequestRepository repository =
                mock(TitleVerificationRequestRepository.class);
        GameProfileRepository profileRepository = mock(GameProfileRepository.class);
        MemberService memberService = mock(MemberService.class);
        BadgeService badgeService = mock(BadgeService.class);
        AdminAuditService auditService = mock(AdminAuditService.class);
        AppProperties properties = new AppProperties(
                new AppProperties.Operator("", "4.5", "PLATINUM", "#8DE9FF"),
                null,
                null,
                null,
                null,
                null,
                null
        );
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
                mock(Clock.class),
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
}
