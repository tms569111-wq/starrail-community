package com.starrailhearing.member.service;

import com.starrailhearing.member.domain.BadgeType;

public record BadgeView(
        String version,
        BadgeType tier,
        String label,
        String colorHex
) {
}
