package com.starrailhearing.web;

import com.starrailhearing.member.service.CurrentMemberProvider;
import com.starrailhearing.member.service.MemberService;
import com.starrailhearing.member.service.BadgeService;
import com.starrailhearing.config.AppProperties;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class SiteModelAdvice {

    private final CurrentMemberProvider currentMemberProvider;
    private final MemberService memberService;
    private final BadgeService badgeService;
    private final AppProperties properties;

    public SiteModelAdvice(
            CurrentMemberProvider currentMemberProvider,
            MemberService memberService,
            BadgeService badgeService,
            AppProperties properties
    ) {
        this.currentMemberProvider = currentMemberProvider;
        this.memberService = memberService;
        this.badgeService = badgeService;
        this.properties = properties;
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
                        member.isNicknameConfigured(),
                        badgeService.find(member.getId(), properties.operator().platinumVersion())
                ))
                .orElse(null);
    }

}
