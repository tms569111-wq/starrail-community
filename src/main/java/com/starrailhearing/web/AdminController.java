package com.starrailhearing.web;

import com.starrailhearing.character.domain.ProfileProvider;
import com.starrailhearing.character.service.CharacterAdminService;
import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.common.exception.ErrorCode;
import com.starrailhearing.evaluation.service.VersionManagementService;
import com.starrailhearing.member.service.BadgeService;
import com.starrailhearing.member.service.CurrentMemberProvider;
import com.starrailhearing.member.service.SuspensionPeriod;
import com.starrailhearing.member.service.TitleVerificationService;
import com.starrailhearing.moderation.domain.ModerationActionType;
import com.starrailhearing.moderation.domain.ReportDecision;
import com.starrailhearing.moderation.service.AdminDashboardService;
import com.starrailhearing.moderation.service.ModerationService;
import com.starrailhearing.profile.client.ResilientGameProfileClient;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
public class AdminController {
    private final CurrentMemberProvider currentMemberProvider;
    private final AdminDashboardService dashboardService;
    private final ModerationService moderationService;
    private final BadgeService badgeService;
    private final VersionManagementService versionService;
    private final CharacterAdminService characterAdminService;
    private final TitleVerificationService titleVerificationService;
    private final ResilientGameProfileClient profileClient;

    public AdminController(
            CurrentMemberProvider currentMemberProvider,
            AdminDashboardService dashboardService,
            ModerationService moderationService,
            BadgeService badgeService,
            VersionManagementService versionService,
            CharacterAdminService characterAdminService,
            TitleVerificationService titleVerificationService,
            ResilientGameProfileClient profileClient
    ) {
        this.currentMemberProvider = currentMemberProvider;
        this.dashboardService = dashboardService;
        this.moderationService = moderationService;
        this.badgeService = badgeService;
        this.versionService = versionService;
        this.characterAdminService = characterAdminService;
        this.titleVerificationService = titleVerificationService;
        this.profileClient = profileClient;
    }

    @GetMapping
    public String dashboard(Model model) {
        long operatorId = currentMemberProvider.requireCurrentMemberId();
        model.addAttribute("dashboard", dashboardService.view(operatorId));
        model.addAttribute("versions", versionService.adminViews(operatorId));
        model.addAttribute("characters", characterAdminService.views(operatorId));
        model.addAttribute("titleRequests", titleVerificationService.adminViews(operatorId));
        model.addAttribute("profileProviders", profileClient.providerStatuses());
        model.addAttribute("profileProviderOptions", ProfileProvider.values());
        model.addAttribute("sanctionOptions", SuspensionPeriod.values());
        return "admin";
    }

    @PostMapping("/members/{memberId}/sanctions")
    public String sanction(
            @PathVariable long memberId,
            @RequestParam String period,
            @RequestParam String reason,
            RedirectAttributes redirect
    ) {
        return run(redirect, "회원 제재를 기록했습니다.", () -> moderationService.sanctionMember(
                currentMemberProvider.requireCurrentMemberId(), memberId,
                SuspensionPeriod.from(period), reason
        ));
    }

    @PostMapping("/members/{memberId}/restore")
    public String restoreMember(
            @PathVariable long memberId,
            @RequestParam String reason,
            RedirectAttributes redirect
    ) {
        return run(redirect, "작성 권한을 복원했습니다.", () -> moderationService.restoreMember(
                currentMemberProvider.requireCurrentMemberId(), memberId, reason
        ));
    }

    @PostMapping("/members/{memberId}/badge/revoke")
    public String revokeBadge(
            @PathVariable long memberId,
            @RequestParam String version,
            RedirectAttributes redirect
    ) {
        return run(redirect, "칭호를 회수했습니다.", () -> {
            long operatorId = currentMemberProvider.requireCurrentMemberId();
            badgeService.revoke(operatorId, memberId, version);
            moderationService.recordBadgeAction(
                    operatorId, memberId, ModerationActionType.REVOKE_BADGE, version + " 칭호 회수"
            );
        });
    }

    @PostMapping("/reports/{reportId}")
    public String decideReport(
            @PathVariable long reportId,
            @RequestParam String decision,
            @RequestParam(defaultValue = "false") boolean hideComment,
            @RequestParam(required = false) String sanction,
            @RequestParam String note,
            RedirectAttributes redirect
    ) {
        return run(redirect, "신고를 처리했습니다.", () -> moderationService.decide(
                currentMemberProvider.requireCurrentMemberId(), reportId,
                ReportDecision.from(decision), hideComment,
                sanction == null || sanction.isBlank() ? null : SuspensionPeriod.from(sanction), note
        ));
    }

    @PostMapping("/comments/{commentId}/restore")
    public String restoreComment(
            @PathVariable long commentId,
            @RequestParam String reason,
            RedirectAttributes redirect
    ) {
        return run(redirect, "댓글을 복원했습니다.", () -> moderationService.restoreComment(
                currentMemberProvider.requireCurrentMemberId(), commentId, reason
        ));
    }

    @PostMapping("/versions")
    public String createVersion(@RequestParam String versionCode, RedirectAttributes redirect) {
        return run(redirect, "버전 초안을 만들었습니다.", () -> versionService.createDraft(
                currentMemberProvider.requireCurrentMemberId(), versionCode
        ));
    }

    @PostMapping("/versions/{versionId}/open")
    public String openVersion(@PathVariable long versionId, RedirectAttributes redirect) {
        return run(redirect, "버전을 열고 규칙 스냅샷을 확정했습니다.", () -> versionService.open(
                currentMemberProvider.requireCurrentMemberId(), versionId
        ));
    }

    @PostMapping("/versions/{versionId}/close")
    public String closeVersion(
            @PathVariable long versionId,
            @RequestParam String confirmation,
            RedirectAttributes redirect
    ) {
        return run(redirect, "최종 집계와 아카이브를 저장하고 버전을 종료했습니다.", () -> {
            boolean closed = versionService.close(
                    currentMemberProvider.requireCurrentMemberId(), versionId, confirmation
            );
            if (!closed) throw new AppException(
                    ErrorCode.VERSION_STATE_CONFLICT,
                    "최종 집계에 실패해 CLOSING 상태로 유지했습니다. 원인을 확인한 뒤 다시 시도해 주세요."
            );
        });
    }

    @PostMapping("/characters")
    public String createCharacter(
            @RequestParam String canonicalExternalId,
            @RequestParam String slug,
            @RequestParam String name,
            @RequestParam int rarity,
            @RequestParam String pathCode,
            @RequestParam String pathName,
            @RequestParam String elementCode,
            @RequestParam String elementName,
            @RequestParam String iconUrl,
            @RequestParam String portraitUrl,
            @RequestParam int displayOrder,
            RedirectAttributes redirect
    ) {
        return run(redirect, "캐릭터를 숨김 상태로 추가했습니다. 외부 ID를 확인한 뒤 공개해 주세요.", () -> characterAdminService.create(
                currentMemberProvider.requireCurrentMemberId(), input(
                        canonicalExternalId, slug, name, rarity, pathCode, pathName,
                        elementCode, elementName, iconUrl, portraitUrl, displayOrder
                )
        ));
    }

    @PostMapping("/characters/{characterId}")
    public String updateCharacter(
            @PathVariable long characterId,
            @RequestParam String canonicalExternalId,
            @RequestParam String slug,
            @RequestParam String name,
            @RequestParam int rarity,
            @RequestParam String pathCode,
            @RequestParam String pathName,
            @RequestParam String elementCode,
            @RequestParam String elementName,
            @RequestParam String iconUrl,
            @RequestParam String portraitUrl,
            @RequestParam int displayOrder,
            RedirectAttributes redirect
    ) {
        return run(redirect, "캐릭터 정보를 수정했습니다.", () -> characterAdminService.update(
                currentMemberProvider.requireCurrentMemberId(), characterId, input(
                        canonicalExternalId, slug, name, rarity, pathCode, pathName,
                        elementCode, elementName, iconUrl, portraitUrl, displayOrder
                )
        ));
    }

    @PostMapping("/characters/{characterId}/visibility")
    public String characterVisibility(
            @PathVariable long characterId,
            @RequestParam boolean visible,
            RedirectAttributes redirect
    ) {
        return run(redirect, visible ? "캐릭터를 공개했습니다." : "캐릭터를 숨겼습니다.",
                () -> characterAdminService.setVisible(
                        currentMemberProvider.requireCurrentMemberId(), characterId, visible
                ));
    }

    @PostMapping("/characters/{characterId}/aliases")
    public String upsertAlias(
            @PathVariable long characterId,
            @RequestParam String provider,
            @RequestParam String externalId,
            RedirectAttributes redirect
    ) {
        return run(redirect, "외부 ID 별칭을 저장했습니다.", () -> characterAdminService.upsertAlias(
                currentMemberProvider.requireCurrentMemberId(), characterId,
                ProfileProvider.valueOf(provider.trim().toUpperCase()), externalId
        ));
    }

    @PostMapping("/title-requests/{requestId}")
    public String decideTitle(
            @PathVariable long requestId,
            @RequestParam boolean approve,
            @RequestParam String note,
            RedirectAttributes redirect
    ) {
        return run(redirect, "칭호 신청을 처리하고 증빙 파일 삭제 절차를 실행했습니다.",
                () -> titleVerificationService.decide(
                        currentMemberProvider.requireCurrentMemberId(), requestId, approve, note
                ));
    }

    @GetMapping("/title-requests/{requestId}/image")
    public ResponseEntity<Resource> titleImage(@PathVariable long requestId) {
        var image = titleVerificationService.image(
                currentMemberProvider.requireCurrentMemberId(), requestId
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(image.mimeType()))
                .body(image.resource());
    }

    private CharacterAdminService.CharacterInput input(
            String canonicalExternalId,
            String slug,
            String name,
            int rarity,
            String pathCode,
            String pathName,
            String elementCode,
            String elementName,
            String iconUrl,
            String portraitUrl,
            int displayOrder
    ) {
        return new CharacterAdminService.CharacterInput(
                canonicalExternalId, slug, name, rarity, pathCode, pathName,
                elementCode, elementName, iconUrl, portraitUrl, displayOrder
        );
    }

    private String run(RedirectAttributes redirect, String success, Runnable action) {
        try {
            action.run();
            redirect.addFlashAttribute("successMessage", success);
        } catch (AppException | IllegalArgumentException exception) {
            redirect.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/admin";
    }
}
