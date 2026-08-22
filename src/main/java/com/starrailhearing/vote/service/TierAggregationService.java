package com.starrailhearing.vote.service;

import com.starrailhearing.evaluation.domain.GameVersion;
import com.starrailhearing.evaluation.domain.VersionStatus;
import com.starrailhearing.evaluation.repository.GameVersionRepository;
import com.starrailhearing.vote.repository.TierAggregateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class TierAggregationService {
    private final GameVersionRepository versionRepository;
    private final TierAggregateRepository aggregateRepository;
    private final Clock clock;

    public TierAggregationService(
            GameVersionRepository versionRepository,
            TierAggregateRepository aggregateRepository,
            Clock clock
    ) {
        this.versionRepository = versionRepository;
        this.aggregateRepository = aggregateRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aggregateVersion(long versionId) {
        GameVersion version = versionRepository.findForUpdateById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("게임 버전을 찾을 수 없습니다."));
        if (version.getStatus() != VersionStatus.OPEN && version.getStatus() != VersionStatus.CLOSING) {
            throw new IllegalStateException("열려 있거나 종료 중인 버전만 집계할 수 있습니다.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        for (EidolonFilter filter : EidolonFilter.values()) {
            aggregateRepository.refreshForFilter(
                    versionId,
                    filter.name(),
                    filter.getMinimum(),
                    filter.getMaximum(),
                    now
            );
        }
        version.aggregationSucceeded(now);
    }

    @Transactional
    public void markFailed(long versionId) {
        versionRepository.findForUpdateById(versionId)
                .filter(version -> version.getStatus() == VersionStatus.OPEN
                        || version.getStatus() == VersionStatus.CLOSING)
                .ifPresent(GameVersion::aggregationFailed);
    }
}
