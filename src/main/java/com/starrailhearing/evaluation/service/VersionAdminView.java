package com.starrailhearing.evaluation.service;

import com.starrailhearing.evaluation.domain.AggregationStatus;
import com.starrailhearing.evaluation.domain.VersionStatus;

import java.time.LocalDateTime;

public record VersionAdminView(
        long id,
        String versionCode,
        VersionStatus status,
        int minimumSample,
        AggregationStatus aggregationStatus,
        LocalDateTime lastAggregatedAt,
        LocalDateTime openedAt,
        LocalDateTime closedAt,
        boolean rulesEditable,
        String rulesSnapshotJson,
        String archiveJson
) {
}
