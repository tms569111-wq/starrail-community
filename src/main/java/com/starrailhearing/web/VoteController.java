package com.starrailhearing.web;

import com.starrailhearing.character.domain.GameCharacter;
import com.starrailhearing.character.service.CharacterService;
import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.member.service.CurrentMemberProvider;
import com.starrailhearing.comment.service.CommentSort;
import com.starrailhearing.vote.service.EidolonFilter;
import com.starrailhearing.vote.service.VoteService;
import com.starrailhearing.vote.service.VoteSubmitResult;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class VoteController {

    private final CharacterService characterService;
    private final VoteService voteService;
    private final CurrentMemberProvider currentMemberProvider;

    public VoteController(
            CharacterService characterService,
            VoteService voteService,
            CurrentMemberProvider currentMemberProvider
    ) {
        this.characterService = characterService;
        this.voteService = voteService;
        this.currentMemberProvider = currentMemberProvider;
    }

    @PostMapping("/characters/{slug}/votes")
    public String vote(
            @PathVariable String slug,
            @RequestParam long optionId,
            @RequestParam(name = "filter", required = false) String filter,
            @RequestParam(name = "sort", required = false) String sort,
            RedirectAttributes redirect
    ) {
        EidolonFilter redirectFilter = EidolonFilter.from(filter);
        try {
            GameCharacter character = characterService.requireActive(slug);
            VoteSubmitResult result = voteService.submit(
                    currentMemberProvider.requireCurrentMemberId(), character, optionId
            );
            redirectFilter = result.countedIn();
            redirect.addFlashAttribute(
                    "successMessage",
                    result.optionLabel() + " 투표를 " + result.countedIn().getLabel() + " 결과에 저장했습니다."
            );
        } catch (AppException exception) {
            redirect.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/characters/" + slug
                + "?filter=" + redirectFilter.name()
                + "&sort=" + CommentSort.from(sort).name();
    }
}
