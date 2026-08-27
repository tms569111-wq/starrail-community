package com.starrailhearing.web;

import com.starrailhearing.member.service.CurrentMemberProvider;
import com.starrailhearing.notification.service.MemberNotificationService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/me/notifications")
public class NotificationController {
    private final CurrentMemberProvider currentMemberProvider;
    private final MemberNotificationService notificationService;

    public NotificationController(
            CurrentMemberProvider currentMemberProvider,
            MemberNotificationService notificationService
    ) {
        this.currentMemberProvider = currentMemberProvider;
        this.notificationService = notificationService;
    }

    @PostMapping("/read")
    public String markRead(
            @RequestParam(name = "notificationIds", required = false) List<Long> notificationIds,
            @RequestParam(name = "returnTo", required = false) String returnTo
    ) {
        notificationService.markRead(
                currentMemberProvider.requireCurrentMemberId(),
                notificationIds == null ? List.of() : notificationIds
        );
        return "redirect:" + safeReturnPath(returnTo);
    }

    private String safeReturnPath(String value) {
        String path = value == null ? "" : value.trim();
        if (!path.startsWith("/") || path.startsWith("//")
                || path.contains("\r") || path.contains("\n")) {
            return "/";
        }
        return path;
    }
}
