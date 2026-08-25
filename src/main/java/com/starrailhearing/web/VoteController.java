package com.starrailhearing.web;

import com.starrailhearing.character.domain.GameCharacter;
import com.starrailhearing.character.service.CharacterService;
import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.evaluation.domain.GameVersion;
import com.starrailhearing.evaluation.service.VersionBrowseService;
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
    private final VersionBrowseService versionBrowseService;

    public VoteController(
            CharacterService characterService,
            VoteService voteService,
            CurrentMemberProvider currentMemberProvider,
            VersionBrowseService versionBrowseService
    ) {
        this.characterService = characterService;
        this.voteService = voteService;
        this.currentMemberProvider = currentMemberProvider;
        this.versionBrowseService = versionBrowseService;
    }

    @PostMapping("/characters/{slug}/votes")
    public String vote(
            @PathVariable String slug,
            @RequestParam long optionId,
            @RequestParam(name = "version", required = false) String version,
            @RequestParam(name = "filter", required = false) String filter,
            @RequestParam(name = "sort", required = false) String sort,
            RedirectAttributes redirect
    ) {
        EidolonFilter redirectFilter = EidolonFilter.from(filter);
        String redirectVersion = null;
        try {
            GameVersion selectedVersion = versionBrowseService.requireSelected(version);
            redirectVersion = selectedVersion.getVersionCode();
            GameCharacter character = characterService.requireActiveForVersion(
                    slug, selectedVersion.getId()
            );
            VoteSubmitResult result = voteService.submit(
                    currentMemberProvider.requireCurrentMemberId(),
                    character,
                    selectedVersion,
                    optionId
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
                + (redirectVersion == null ? "?" : "?version=" + redirectVersion + "&")
                + "filter=" + redirectFilter.name()
                + "&sort=" + CommentSort.from(sort).name();
    }
}
