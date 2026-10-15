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
                        @RequestParam(value = "requested", required = false) String requested,
                        @RequestParam(value = "pending", required = false) String pending,
                        @RequestParam(value = "locked", required = false) String locked,
                        Model model) {
        if (error != null) {
            model.addAttribute("error", "잘못된 사용자명 또는 비밀번호입니다");
        }
        if (locked != null) {
            model.addAttribute("error", "계정이 잠겼습니다. 30분 후에 다시 시도해주세요.");
        }
        if (pending != null) {
            model.addAttribute("error", "관리자 승인 대기 중인 계정입니다.");
        }
        if (logout != null) {
            model.addAttribute("message", "로그아웃되었습니다");
        }
        if (requested != null) {
            model.addAttribute("message", "계정 신청이 접수되었습니다. 관리자 승인 후 로그인할 수 있습니다.");
        }
        model.addAttribute("registrationEnabled", registrationEnabled);
        return "login";
    }
}
