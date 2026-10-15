package com.example.authserver.config;

import com.example.authserver.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class CustomAuthenticationFailureHandler implements AuthenticationFailureHandler {

    private final UserRepository userRepository;

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCK_DURATION_MINUTES = 30;

    @Override
    @Transactional
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        // 승인 대기(비활성) 계정 — 실패 횟수를 올리지 않는다(Spring은 비밀번호보다 enabled를 먼저 본다).
        if (exception instanceof DisabledException) {
            response.sendRedirect("/login?pending");
            return;
        }

        String username = request.getParameter("username");

        userRepository.findByUsername(username).ifPresent(user -> {
            if (user.isAccountNonLocked()) {
                if (user.getFailedAttempt() < MAX_FAILED_ATTEMPTS - 1) {
                    user.setFailedAttempt(user.getFailedAttempt() + 1);
                    userRepository.save(user);
                } else {
                    user.setAccountNonLocked(false);
                    user.setLockTime(LocalDateTime.now());
                    user.setFailedAttempt(MAX_FAILED_ATTEMPTS);
                    userRepository.save(user);
                }
            } else if (user.getLockTime() != null &&
                    user.getLockTime().plusMinutes(LOCK_DURATION_MINUTES).isBefore(LocalDateTime.now())) {
                // 잠금 해제
                user.setAccountNonLocked(true);
                user.setFailedAttempt(0);
                user.setLockTime(null);
                userRepository.save(user);
            }
        });

        String errorMessage = "error";
        if (exception.getMessage().contains("locked")) {
            errorMessage = "locked";
        }

        response.sendRedirect("/login?" + errorMessage);
    }
}
