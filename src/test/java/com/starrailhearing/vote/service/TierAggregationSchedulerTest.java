package com.starrailhearing.vote.service;

import com.starrailhearing.evaluation.domain.GameVersion;
import com.starrailhearing.evaluation.domain.VersionStatus;
import com.starrailhearing.evaluation.repository.GameVersionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TierAggregationSchedulerTest {

    @Test
    void 한_버전의_실패상태_저장까지_실패해도_다음_버전을_계속_집계한다() {
        GameVersionRepository versionRepository = mock(GameVersionRepository.class);
        TierAggregationService aggregationService = mock(TierAggregationService.class);
        GameVersion first = mock(GameVersion.class);
        GameVersion second = mock(GameVersion.class);
        when(first.getId()).thenReturn(44L);
        when(second.getId()).thenReturn(45L);
        when(versionRepository.findAllByStatusIn(List.of(VersionStatus.OPEN)))
                .thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("aggregation failed"))
                .when(aggregationService).aggregateVersion(44L);
        doThrow(new IllegalStateException("mark failed"))
                .when(aggregationService).markFailed(44L);
        TierAggregationScheduler scheduler = new TierAggregationScheduler(
                versionRepository,
                aggregationService
        );

        scheduler.refreshOpenVersions();

        verify(aggregationService).aggregateVersion(44L);
        verify(aggregationService).markFailed(44L);
        verify(aggregationService).aggregateVersion(45L);
    }
}
