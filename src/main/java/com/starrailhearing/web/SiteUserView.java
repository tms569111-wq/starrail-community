package com.starrailhearing.web;

import com.starrailhearing.member.service.BadgeView;

public record SiteUserView(
        long id,
        String nickname,
        boolean admin,
        boolean canWrite,
        boolean nicknameConfigured,
        BadgeView badge
) {
}
