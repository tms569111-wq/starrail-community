package com.starrailhearing.notification.service;

import java.time.LocalDateTime;

public record MemberNotificationView(
        long id,
        String title,
        String message,
        LocalDateTime createdAt
) {
}
