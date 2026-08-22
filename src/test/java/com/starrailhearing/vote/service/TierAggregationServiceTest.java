package com.starrailhearing.vote.service;

import com.starrailhearing.evaluation.domain.GameVersion;
import com.starrailhearing.evaluation.domain.VersionStatus;
import com.starrailhearing.evaluation.repository.GameVersionRepository;
import com.starrailhearing.vote.repository.TierAggregateRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TierAggregationServiceTest {

    @Test
    void 여섯_성혼_필터를_갱신한_뒤_버전_집계상태를_성공으로_바꾼다() {
        GameVersionRepository versionRepository = mock(GameVersionRepository.class);
        TierAggregateRepository aggregateRepository = mock(TierAggregateRepository.class);
        GameVersion version = mock(GameVersion.class);
        when(version.getStatus()).thenReturn(VersionStatus.OPEN);
        when(versionRepository.findForUpdateById(44L)).thenReturn(Optional.of(version));
        TierAggregationService service = new TierAggregationService(
                versionRepository,
                aggregateRepository,
                Clock.fixed(Instant.parse("2026-08-18T00:00:00Z"), ZoneOffset.UTC)
        );

        service.aggregateVersion(44L);

        verify(aggregateRepository, times(EidolonFilter.values().length)).refreshForFilter(
                eq(44L), any(String.class), any(), any(), any()
        );
        verify(version).aggregationSucceeded(any());
    }
}
