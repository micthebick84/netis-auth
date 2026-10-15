package com.example.authserver.controller;

import com.example.authserver.entity.User;
import com.example.authserver.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.Optional;

/**
 * 계정 신청 승인 화면 (ROLE_ADMIN — SecurityConfig의 /admin/**).
 * 신청 계정은 비활성으로 저장되고, 승인 = 활성화, 거절 = 삭제(같은 이름으로 재신청 가능).
 */
@Controller
@RequestMapping("/admin/signups")
@RequiredArgsConstructor
@Slf4j
public class SignupAdminController {

    static final String NOT_PENDING = "대기 중인 신청이 아닙니다 (이미 처리되었을 수 있습니다)";

    private final UserService userService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("pending", userService.listPendingSignups());
        return "admin-signups";
    }

    @PostMapping("/{id}/approve")
    public String approve(@PathVariable Long id, Principal admin, RedirectAttributes redirect) {
        Optional<User> done = userService.approveSignup(id);
        done.ifPresent(u -> log.info("계정 신청 승인: {} (관리자 {})", u.getUsername(), admin.getName()));
        return result(done, "%s 계정을 승인했습니다", redirect);
    }

    @PostMapping("/{id}/reject")
    public String reject(@PathVariable Long id, Principal admin, RedirectAttributes redirect) {
        Optional<User> done = userService.rejectSignup(id);
        done.ifPresent(u -> log.info("계정 신청 거절: {} (관리자 {})", u.getUsername(), admin.getName()));
        return result(done, "%s 신청을 거절했습니다", redirect);
    }

    private String result(Optional<User> done, String messageFormat, RedirectAttributes redirect) {
        if (done.isPresent()) {
            redirect.addFlashAttribute("message", messageFormat.formatted(done.get().getUsername()));
        } else {
            redirect.addFlashAttribute("error", NOT_PENDING);
        }
        return "redirect:/admin/signups";
    }
}
