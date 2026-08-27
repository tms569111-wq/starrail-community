package com.starrailhearing.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LegalController {
    private final String contactEmail;

    public LegalController(@Value("${app.site.contact-email:}") String contactEmail) {
        this.contactEmail = contactEmail == null ? "" : contactEmail.trim();
    }

    @GetMapping("/privacy")
    public String privacy(Model model) {
        model.addAttribute("siteContactEmail", contactEmail);
        return "privacy";
    }

    @GetMapping("/terms")
    public String terms(Model model) {
        model.addAttribute("siteContactEmail", contactEmail);
        return "terms";
    }
}
