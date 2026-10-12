package com.example.authserver.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LoginController {

    // 가입이 막혀 있으면(SecurityConfig가 /register를 denyAll) 로그인 화면에서 회원가입 링크를 숨긴다.
    @Value("${app.registration-enabled:true}")
    private boolean registrationEnabled;

    @GetMapping("/login")
    public String login(@RequestParam(value = "error", required = false) String error,
                        @RequestParam(value = "logout", required = false) String logout,
                        @RequestParam(value = "registered", required = false) String registered,
                        @RequestParam(value = "locked", required = false) String locked,
                        Model model) {
        if (error != null) {
            model.addAttribute("error", "잘못된 사용자명 또는 비밀번호입니다");
        }
        if (locked != null) {
            model.addAttribute("error", "계정이 잠겼습니다. 30분 후에 다시 시도해주세요.");
        }
        if (logout != null) {
            model.addAttribute("message", "로그아웃되었습니다");
        }
        if (registered != null) {
            model.addAttribute("message", "회원가입이 완료되었습니다. 로그인해주세요.");
        }
        model.addAttribute("registrationEnabled", registrationEnabled);
        return "login";
    }
}
