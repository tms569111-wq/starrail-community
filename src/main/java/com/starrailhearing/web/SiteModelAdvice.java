package com.starrailhearing.web;

import com.starrailhearing.member.service.CurrentMemberProvider;
import com.starrailhearing.member.service.MemberService;
import com.starrailhearing.member.service.BadgeService;
import com.starrailhearing.notification.service.MemberNotificationService;
import com.starrailhearing.notification.service.MemberNotificationView;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

@ControllerAdvice
public class SiteModelAdvice {

    private final CurrentMemberProvider currentMemberProvider;
    private final MemberService memberService;
    private final BadgeService badgeService;
    private final MemberNotificationService notificationService;

    public SiteModelAdvice(
            CurrentMemberProvider currentMemberProvider,
            MemberService memberService,
            BadgeService badgeService,
            MemberNotificationService notificationService
    ) {
        this.currentMemberProvider = currentMemberProvider;
        this.memberService = memberService;
        this.badgeService = badgeService;
        this.notificationService = notificationService;
    }

    @ModelAttribute("currentUser")
    public SiteUserView currentUser() {
        return currentMemberProvider.findCurrentMemberId()
                .map(memberService::requireReadable)
                .map(member -> new SiteUserView(
                        member.getId(),
                        member.getNickname(),
                        member.isAdmin(),
                        member.isActive() && member.isNicknameConfigured(),
                        member.isActive(),
                        member.isNicknameConfigured(),
                        badgeService.findLatestActive(member.getId())
                ))
                .orElse(null);
    }

    @ModelAttribute("notifications")
    public List<MemberNotificationView> notifications() {
        return currentMemberProvider.findCurrentMemberId()
                .map(notificationService::unread)
                .orElseGet(List::of);
    }

    @ModelAttribute("currentRequestPath")
    public String currentRequestPath(HttpServletRequest request) {
        String query = request.getQueryString();
        return request.getRequestURI() + (query == null || query.isBlank() ? "" : "?" + query);
    }

}
