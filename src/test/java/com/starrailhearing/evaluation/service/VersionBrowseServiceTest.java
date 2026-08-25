package com.starrailhearing.evaluation.service;

import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.common.exception.ErrorCode;
import com.starrailhearing.evaluation.domain.GameVersion;
import com.starrailhearing.evaluation.domain.VersionStatus;
import com.starrailhearing.evaluation.repository.GameVersionRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VersionBrowseServiceTest {

    @Test
    void 버전을_고르지_않으면_진행_중인_버전을_선택한다() {
        GameVersionRepository repository = mock(GameVersionRepository.class);
        GameVersion openVersion = mock(GameVersion.class);
        when(repository.findFirstByStatusOrderByOpenedAtDesc(VersionStatus.OPEN))
                .thenReturn(Optional.of(openVersion));

        VersionBrowseService service = new VersionBrowseService(repository);

        assertThat(service.findSelected(null)).contains(openVersion);
    }

    @Test
    void 준비_중인_초안은_공개_버전으로_조회할_수_없다() {
        GameVersionRepository repository = mock(GameVersionRepository.class);
        GameVersion draft = mock(GameVersion.class);
        when(draft.getStatus()).thenReturn(VersionStatus.DRAFT);
        when(repository.findByVersionCode("4.5")).thenReturn(Optional.of(draft));

        VersionBrowseService service = new VersionBrowseService(repository);

        assertThatThrownBy(() -> service.requireSelected("4.5"))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VERSION_NOT_FOUND)
                );
    }
}
