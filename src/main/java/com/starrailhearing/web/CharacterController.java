package com.starrailhearing.web;

import com.starrailhearing.character.domain.GameCharacter;
import com.starrailhearing.character.service.CharacterService;
import com.starrailhearing.comment.service.CommentService;
import com.starrailhearing.comment.service.CommentSort;
import com.starrailhearing.member.service.CurrentMemberProvider;
import com.starrailhearing.vote.service.EidolonFilter;
import com.starrailhearing.vote.service.VoteService;
import com.starrailhearing.moderation.domain.ReportReason;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class CharacterController {

    private final CharacterService characterService;
    private final VoteService voteService;
    private final CommentService commentService;
    private final CurrentMemberProvider currentMemberProvider;

    public CharacterController(
            CharacterService characterService,
            VoteService voteService,
            CommentService commentService,
            CurrentMemberProvider currentMemberProvider
    ) {
        this.characterService = characterService;
        this.voteService = voteService;
        this.commentService = commentService;
        this.currentMemberProvider = currentMemberProvider;
    }

    @GetMapping("/characters/{slug}")
    public String detail(
            @PathVariable String slug,
            @RequestParam(name = "filter", required = false) String filterValue,
            @RequestParam(name = "sort", required = false) String sortValue,
            @RequestParam(name = "page", defaultValue = "0") int page,
            Model model
    ) {
        GameCharacter character = characterService.requireActive(slug);
        EidolonFilter filter = EidolonFilter.from(filterValue);
        CommentSort sort = CommentSort.from(sortValue);
        Long memberId = currentMemberProvider.findCurrentMemberId().orElse(null);

        model.addAttribute("character", character);
        model.addAttribute("tier", voteService.view(memberId, character, filter));
        model.addAttribute("commentPage", commentService.view(memberId, character, filter, sort, page));
        model.addAttribute("filters", EidolonFilter.values());
        model.addAttribute("sorts", CommentSort.values());
        model.addAttribute("reportReasons", ReportReason.values());
        return "character-detail";
    }
}
