package com.starrailhearing.web;

import com.starrailhearing.character.domain.GameCharacter;
import com.starrailhearing.character.service.CharacterService;
import com.starrailhearing.vote.service.VoteService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class HomeController {

    private static final List<FilterOption> ELEMENTS = List.of(
            new FilterOption("", "전체 속성"),
            new FilterOption("Physical", "물리"),
            new FilterOption("Fire", "화염"),
            new FilterOption("Ice", "얼음"),
            new FilterOption("Thunder", "번개"),
            new FilterOption("Wind", "바람"),
            new FilterOption("Quantum", "양자"),
            new FilterOption("Imaginary", "허수")
    );

    private static final List<FilterOption> PATHS = List.of(
            new FilterOption("", "전체 운명의 길"),
            new FilterOption("Warrior", "파멸"),
            new FilterOption("Rogue", "수렵"),
            new FilterOption("Mage", "지식"),
            new FilterOption("Shaman", "화합"),
            new FilterOption("Warlock", "공허"),
            new FilterOption("Knight", "보존"),
            new FilterOption("Priest", "풍요"),
            new FilterOption("Memory", "기억"),
            new FilterOption("Elation", "환락")
    );

    private final CharacterService characterService;
    private final VoteService voteService;

    public HomeController(CharacterService characterService, VoteService voteService) {
        this.characterService = characterService;
        this.voteService = voteService;
    }

    @GetMapping("/")
    public String home(
            @RequestParam(name = "q", required = false) String keyword,
            @RequestParam(name = "element", required = false) String element,
            @RequestParam(name = "path", required = false) String path,
            @RequestParam(name = "withdrawn", required = false) String withdrawn,
        Model model
    ) {
        List<GameCharacter> characters = characterService.search(keyword, element, path);
        model.addAttribute("tierRows", voteService.tierBoard(characters));
        model.addAttribute("characterCount", characters.size());
        model.addAttribute("keyword", keyword == null ? "" : keyword);
        model.addAttribute("selectedElement", element == null ? "" : element);
        model.addAttribute("selectedPath", path == null ? "" : path);
        model.addAttribute("elements", ELEMENTS);
        model.addAttribute("paths", PATHS);
        model.addAttribute("heroCharacter", characters.isEmpty() ? null : characters.get(0));
        model.addAttribute("currentVersion", voteService.currentVersionCode());
        model.addAttribute("withdrawn", withdrawn != null);
        return "home";
    }

    public record FilterOption(String value, String label) {
    }
}
