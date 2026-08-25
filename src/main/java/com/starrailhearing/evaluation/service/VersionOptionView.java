package com.starrailhearing.evaluation.service;

import com.starrailhearing.evaluation.domain.VersionStatus;

public record VersionOptionView(
        String versionCode,
        VersionStatus status,
        String statusLabel
) {
}
