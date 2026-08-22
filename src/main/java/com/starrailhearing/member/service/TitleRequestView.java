package com.starrailhearing.member.service;

import com.starrailhearing.member.domain.TitleRequestStatus;

import java.time.LocalDateTime;

public record TitleRequestView(
        long id,
        long memberId,
        String memberNickname,
        String gameVersion,
        TitleRequestStatus status,
        String reviewNote,
        LocalDateTime expiresAt,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt,
        boolean imageAvailable,
        String imageMimeType
) {
}
