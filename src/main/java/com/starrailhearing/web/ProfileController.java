package com.starrailhearing.web;

import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.common.exception.ErrorCode;
import com.starrailhearing.member.service.CurrentMemberProvider;
import com.starrailhearing.profile.service.ProfileSyncResult;
import com.starrailhearing.profile.service.ProfileVerificationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/me/profile")
public class ProfileController {

    private final ProfileVerificationService profileService;
    private final CurrentMemberProvider currentMemberProvider;

    public ProfileController(
            ProfileVerificationService profileService,
            CurrentMemberProvider currentMemberProvider
    ) {
        this.profileService = profileService;
        this.currentMemberProvider = currentMemberProvider;
    }

    @GetMapping
    public String profile(Model model) {
        model.addAttribute("profile", profileService.view(currentMemberProvider.requireCurrentMemberId()));
        return "profile";
    }

    @PostMapping("/challenge")
    public String challenge(
            @RequestParam String uid,
            RedirectAttributes redirect
    ) {
        runSync(
                () -> profileService.verifyUid(currentMemberProvider.requireCurrentMemberId(), uid),
                redirect,
                "UID 인증 완료"
        );
        return "redirect:/me/profile";
    }

    @PostMapping("/verify")
    public String verify(RedirectAttributes redirect) {
        runSync(() -> profileService.verify(currentMemberProvider.requireCurrentMemberId()), redirect, "인증 완료");
        return "redirect:/me/profile";
    }

    @PostMapping("/refresh")
    public String refresh(RedirectAttributes redirect) {
        runSync(() -> profileService.refresh(currentMemberProvider.requireCurrentMemberId()), redirect, "새로고침 완료");
        return "redirect:/me/profile";
    }

    private void runSync(
            java.util.function.Supplier<ProfileSyncResult> action,
            RedirectAttributes redirect,
            String prefix
    ) {
        try {
            ProfileSyncResult result = action.get();
            redirect.addFlashAttribute(
                    "successMessage",
                    "%s: %d명 확인, %d명 추가, %d명 성혼 갱신".formatted(
                            prefix, result.seen(), result.added(), result.upgraded()
                    )
            );
        } catch (AppException exception) {
            if (exception.getErrorCode() != ErrorCode.PROFILE_SYNC_COOLDOWN) {
                redirect.addFlashAttribute("errorMessage", exception.getMessage());
            }
        }
    }
}
