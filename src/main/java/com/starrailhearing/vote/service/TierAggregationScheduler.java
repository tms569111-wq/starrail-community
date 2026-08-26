package com.starrailhearing.vote.service;

import com.starrailhearing.evaluation.domain.VersionStatus;
import com.starrailhearing.evaluation.repository.GameVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

@Component
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class TierAggregationScheduler {
    private static final Logger log = LoggerFactory.getLogger(TierAggregationScheduler.class);

    private final GameVersionRepository versionRepository;
    private final TierAggregationService aggregationService;

    public TierAggregationScheduler(
            GameVersionRepository versionRepository,
            TierAggregationService aggregationService
    ) {
        this.versionRepository = versionRepository;
        this.aggregationService = aggregationService;
    }

    @Scheduled(initialDelayString = "10s", fixedDelayString = "${app.aggregation.interval:3m}")
    public void refreshOpenVersions() {
        for (var version : versionRepository.findAllByStatusIn(List.of(VersionStatus.OPEN))) {
            try {
                aggregationService.aggregateVersion(version.getId());
            } catch (RuntimeException exception) {
                log.error("Tier aggregation failed versionId={}", version.getId(), exception);
                try {
                    aggregationService.markFailed(version.getId());
                } catch (RuntimeException markFailure) {
                    log.error("Tier aggregation failure status update failed versionId={}",
                            version.getId(), markFailure);
                }
            }
        }
    }
}
