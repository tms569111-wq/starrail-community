package com.starrailhearing.web;

import com.starrailhearing.member.service.CurrentMemberProvider;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {
    private final CurrentMemberProvider currentMemberProvider;

    public LoginController(CurrentMemberProvider currentMemberProvider) {
        this.currentMemberProvider = currentMemberProvider;
    }

    @GetMapping("/login")
    public String login() {
        return currentMemberProvider.findCurrentMemberId().isPresent() ? "redirect:/" : "login";
    }
}
