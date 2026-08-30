package com.starrailhearing.web;

import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.member.service.BadgeService;
import com.starrailhearing.member.service.CurrentMemberProvider;
import com.starrailhearing.member.service.MemberService;
import com.starrailhearing.member.service.AccountWithdrawalService;
import com.starrailhearing.member.service.TitleVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/me/account")
public class AccountController {
    private final CurrentMemberProvider currentMemberProvider;
    private final MemberService memberService;
    private final BadgeService badgeService;
    private final TitleVerificationService titleVerificationService;
    private final AccountWithdrawalService withdrawalService;

    public AccountController(
            CurrentMemberProvider currentMemberProvider,
            MemberService memberService,
            BadgeService badgeService,
            TitleVerificationService titleVerificationService,
            AccountWithdrawalService withdrawalService
    ) {
        this.currentMemberProvider = currentMemberProvider;
        this.memberService = memberService;
        this.badgeService = badgeService;
        this.titleVerificationService = titleVerificationService;
        this.withdrawalService = withdrawalService;
    }

    @GetMapping
    public String account(Model model) {
        long memberId = currentMemberProvider.requireCurrentMemberId();
        model.addAttribute("member", memberService.requireReadable(memberId));
        model.addAttribute("badges", badgeService.activeBadges(memberId));
        model.addAttribute("titleVersions", titleVerificationService.applicationVersions());
        model.addAttribute("titleTiers", titleVerificationService.applicationTiers());
        model.addAttribute("titleProfileVerified", titleVerificationService.hasVerifiedProfile(memberId));
        model.addAttribute("titleRequests", titleVerificationService.memberViews(memberId));
        return "account";
    }

    @PostMapping("/nickname")
    public String nickname(@RequestParam String nickname, RedirectAttributes redirect) {
        try {
            memberService.changeNickname(currentMemberProvider.requireCurrentMemberId(), nickname);
            redirect.addFlashAttribute("successMessage", "닉네임을 변경했습니다.");
        } catch (AppException | IllegalArgumentException exception) {
            redirect.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/me/account";
    }

    @PostMapping("/title-requests/{requestId}/cancel")
    public String cancelTitleRequest(
            @PathVariable long requestId,
            RedirectAttributes redirect
    ) {
        try {
            titleVerificationService.cancel(
                    currentMemberProvider.requireCurrentMemberId(), requestId
            );
            redirect.addFlashAttribute("successMessage", "칭호 인증 신청을 취소했습니다. 새 이미지로 다시 신청할 수 있습니다.");
        } catch (AppException | IllegalArgumentException exception) {
            redirect.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/me/account#title-verification";
    }

    @PostMapping("/title-requests")
    public String requestTitle(
            @RequestParam String version,
            @RequestParam String tier,
            @RequestParam("image") MultipartFile image,
            RedirectAttributes redirect
    ) {
        try {
            titleVerificationService.submit(
                    currentMemberProvider.requireCurrentMemberId(), version, tier, image
            );
            redirect.addFlashAttribute("successMessage", "칭호 인증 신청을 접수했습니다.");
        } catch (AppException | IllegalArgumentException exception) {
            redirect.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/me/account#title-verification";
    }

    @PostMapping("/withdraw")
    public String withdraw(
            @RequestParam String confirmation,
            HttpServletRequest request,
            RedirectAttributes redirect
    ) {
        try {
            withdrawalService.withdraw(
                    currentMemberProvider.requireCurrentMemberId(), confirmation
            );
            var session = request.getSession(false);
            if (session != null) session.invalidate();
            SecurityContextHolder.clearContext();
            return "redirect:/?withdrawn";
        } catch (AppException | IllegalArgumentException exception) {
            redirect.addFlashAttribute("errorMessage", exception.getMessage());
            return "redirect:/me/account";
        }
    }
}
