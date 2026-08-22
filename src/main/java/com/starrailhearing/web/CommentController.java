package com.starrailhearing.web;

import com.starrailhearing.character.domain.GameCharacter;
import com.starrailhearing.character.service.CharacterService;
import com.starrailhearing.comment.service.CommentService;
import com.starrailhearing.comment.service.CommentSort;
import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.member.service.CurrentMemberProvider;
import com.starrailhearing.vote.service.EidolonFilter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class CommentController {

    private final CharacterService characterService;
    private final CommentService commentService;
    private final CurrentMemberProvider currentMemberProvider;

    public CommentController(
            CharacterService characterService,
            CommentService commentService,
            CurrentMemberProvider currentMemberProvider
    ) {
        this.characterService = characterService;
        this.commentService = commentService;
        this.currentMemberProvider = currentMemberProvider;
    }

    @PostMapping("/characters/{slug}/comments")
    public String create(
            @PathVariable String slug,
            @RequestParam String content,
            @RequestParam(name = "filter", required = false) String filter,
            @RequestParam(name = "sort", required = false) String sort,
            RedirectAttributes redirect
    ) {
        EidolonFilter redirectFilter = EidolonFilter.from(filter);
        try {
            GameCharacter character = characterService.requireActive(slug);
            redirectFilter = commentService.create(
                    currentMemberProvider.requireCurrentMemberId(), character, content
            );
            redirect.addFlashAttribute(
                    "successMessage",
                    redirectFilter.getLabel() + " 보유 유저 댓글로 등록했습니다."
            );
        } catch (AppException exception) {
            redirect.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return commentRedirect(slug, redirectFilter.name(), sort);
    }

    @PostMapping("/characters/{slug}/comments/{parentId}/replies")
    public String createReply(
            @PathVariable String slug,
            @PathVariable long parentId,
            @RequestParam String content,
            @RequestParam(name = "filter", required = false) String filter,
            @RequestParam(name = "sort", required = false) String sort,
            RedirectAttributes redirect
    ) {
        EidolonFilter redirectFilter = EidolonFilter.from(filter);
        try {
            GameCharacter character = characterService.requireActive(slug);
            redirectFilter = commentService.createReply(
                    currentMemberProvider.requireCurrentMemberId(), character, parentId, content
            );
            redirect.addFlashAttribute("successMessage", "답글을 등록했습니다.");
        } catch (AppException exception) {
            redirect.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return commentRedirect(slug, redirectFilter.name(), sort);
    }

    @GetMapping("/characters/{slug}/comments/{parentId}/replies")
    public String replies(
            @PathVariable String slug,
            @PathVariable long parentId,
            @RequestParam(name = "filter", required = false) String filter,
            @RequestParam(name = "sort", required = false) String sort,
            Model model
    ) {
        GameCharacter character = characterService.requireActive(slug);
        Long memberId = currentMemberProvider.findCurrentMemberId().orElse(null);
        var thread = commentService.replies(memberId, character, parentId);
        model.addAttribute("replies", thread.replies());
        model.addAttribute("totalReplies", thread.totalReplies());
        model.addAttribute("character", character);
        model.addAttribute("authenticated", memberId != null);
        model.addAttribute("canWrite", commentService.canWrite(memberId));
        model.addAttribute("canReply", commentService.canComment(memberId, character.getId()));
        model.addAttribute("filter", EidolonFilter.from(filter));
        model.addAttribute("sort", CommentSort.from(sort));
        model.addAttribute("reportReasons", com.starrailhearing.moderation.domain.ReportReason.values());
        return "fragments/comments :: replies";
    }

    @PostMapping("/characters/{slug}/comments/{commentId}/like")
    public String like(
            @PathVariable String slug,
            @PathVariable long commentId,
            @RequestParam(name = "filter", required = false) String filter,
            @RequestParam(name = "sort", required = false) String sort,
            RedirectAttributes redirect
    ) {
        try {
            GameCharacter character = characterService.requireActive(slug);
            boolean liked = commentService.toggleLike(
                    currentMemberProvider.requireCurrentMemberId(), commentId, character.getId()
            );
            redirect.addFlashAttribute("successMessage", liked ? "댓글을 추천했습니다." : "추천을 취소했습니다.");
        } catch (AppException exception) {
            redirect.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return commentRedirect(slug, filter, sort);
    }

    @PostMapping("/characters/{slug}/comments/{commentId}/delete")
    public String delete(
            @PathVariable String slug,
            @PathVariable long commentId,
            @RequestParam(name = "filter", required = false) String filter,
            @RequestParam(name = "sort", required = false) String sort,
            RedirectAttributes redirect
    ) {
        try {
            GameCharacter character = characterService.requireActive(slug);
            commentService.delete(currentMemberProvider.requireCurrentMemberId(), commentId, character.getId());
            redirect.addFlashAttribute("successMessage", "댓글을 삭제했습니다.");
        } catch (AppException exception) {
            redirect.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return commentRedirect(slug, filter, sort);
    }

    @PostMapping("/characters/{slug}/comments/{commentId}/edit")
    public String edit(
            @PathVariable String slug,
            @PathVariable long commentId,
            @RequestParam String content,
            @RequestParam(name = "filter", required = false) String filter,
            @RequestParam(name = "sort", required = false) String sort,
            RedirectAttributes redirect
    ) {
        try {
            GameCharacter character = characterService.requireActive(slug);
            commentService.edit(
                    currentMemberProvider.requireCurrentMemberId(), commentId, character.getId(), content
            );
            redirect.addFlashAttribute("successMessage", "댓글을 수정했습니다.");
        } catch (AppException exception) {
            redirect.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return commentRedirect(slug, filter, sort);
    }

    private String commentRedirect(String slug, String filter, String sort) {
        return "redirect:/characters/" + slug
                + "?filter=" + EidolonFilter.from(filter).name()
                + "&sort=" + CommentSort.from(sort).name()
                + "#comments";
    }
}
