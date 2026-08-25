package com.starrailhearing.web;

import com.starrailhearing.character.domain.GameCharacter;
import com.starrailhearing.character.service.CharacterService;
import com.starrailhearing.comment.service.CommentService;
import com.starrailhearing.comment.service.CommentSort;
import com.starrailhearing.evaluation.domain.GameVersion;
import com.starrailhearing.evaluation.domain.VersionStatus;
import com.starrailhearing.evaluation.service.VersionBrowseService;
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
    private final VersionBrowseService versionBrowseService;

    public CharacterController(
            CharacterService characterService,
            VoteService voteService,
            CommentService commentService,
            CurrentMemberProvider currentMemberProvider,
            VersionBrowseService versionBrowseService
    ) {
        this.characterService = characterService;
        this.voteService = voteService;
        this.commentService = commentService;
        this.currentMemberProvider = currentMemberProvider;
        this.versionBrowseService = versionBrowseService;
    }

    @GetMapping("/characters/{slug}")
    public String detail(
            @PathVariable String slug,
            @RequestParam(name = "version", required = false) String versionValue,
            @RequestParam(name = "filter", required = false) String filterValue,
            @RequestParam(name = "sort", required = false) String sortValue,
            @RequestParam(name = "page", defaultValue = "0") int page,
            Model model
    ) {
        GameVersion version = versionBrowseService.requireSelected(versionValue);
        GameCharacter character = characterService.requireActiveForVersion(slug, version.getId());
        EidolonFilter filter = EidolonFilter.from(filterValue);
        CommentSort sort = CommentSort.from(sortValue);
        Long memberId = currentMemberProvider.findCurrentMemberId().orElse(null);

        model.addAttribute("character", character);
        model.addAttribute("tier", voteService.view(memberId, character, version, filter));
        model.addAttribute("commentPage", commentService.view(
                memberId, character, version, filter, sort, page
        ));
        model.addAttribute("filters", EidolonFilter.values());
        model.addAttribute("sorts", CommentSort.values());
        model.addAttribute("reportReasons", ReportReason.values());
        model.addAttribute("versions", versionBrowseService.options());
        model.addAttribute("selectedVersionCode", version.getVersionCode());
        model.addAttribute("versionReadOnly", version.getStatus() != VersionStatus.OPEN);
        return "character-detail";
    }
}
