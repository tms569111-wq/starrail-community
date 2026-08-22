package com.starrailhearing.web;

import com.starrailhearing.character.domain.GameCharacter;
import com.starrailhearing.character.service.CharacterService;
import com.starrailhearing.comment.service.CommentSort;
import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.member.service.CurrentMemberProvider;
import com.starrailhearing.moderation.domain.ReportReason;
import com.starrailhearing.moderation.service.ModerationService;
import com.starrailhearing.vote.service.EidolonFilter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class CommunitySafetyController {
    private final CharacterService characterService;
    private final CurrentMemberProvider currentMemberProvider;
    private final ModerationService moderationService;

    public CommunitySafetyController(
            CharacterService characterService,
            CurrentMemberProvider currentMemberProvider,
            ModerationService moderationService
    ) {
        this.characterService = characterService;
        this.currentMemberProvider = currentMemberProvider;
        this.moderationService = moderationService;
    }

    @PostMapping("/characters/{slug}/comments/{commentId}/report")
    public String report(
            @PathVariable String slug,
            @PathVariable long commentId,
            @RequestParam String reason,
            @RequestParam(required = false) String details,
            @RequestParam(name = "filter", required = false) String filter,
            @RequestParam(name = "sort", required = false) String sort,
            RedirectAttributes redirect
    ) {
        try {
            GameCharacter character = characterService.requireActive(slug);
            moderationService.reportComment(
                    currentMemberProvider.requireCurrentMemberId(),
                    commentId,
                    character.getId(),
                    ReportReason.from(reason),
                    details
            );
            redirect.addFlashAttribute("successMessage", "신고를 접수했습니다.");
        } catch (AppException | IllegalArgumentException exception) {
            redirect.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return redirect(slug, filter, sort);
    }

    private String redirect(String slug, String filter, String sort) {
        return "redirect:/characters/" + slug
                + "?filter=" + EidolonFilter.from(filter).name()
                + "&sort=" + CommentSort.from(sort).name()
                + "#comments";
    }
}
