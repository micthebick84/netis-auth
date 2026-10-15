package com.example.authserver.service;

import com.example.authserver.dto.LoginRequest;
import com.example.authserver.dto.LoginResponse;
import com.example.authserver.dto.RegisterRequest;
import com.example.authserver.entity.User;
import com.example.authserver.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCK_TIME_DURATION_MINUTES = 30;

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BadCredentialsException("잘못된 사용자명 또는 비밀번호입니다"));

        // 계정 잠금 확인
        if (!user.isAccountNonLocked()) {
            if (isLockTimeExpired(user)) {
                unlockUser(user);
            } else {
                throw new LockedException("계정이 잠겼습니다. 30분 후에 다시 시도해주세요.");
            }
        }

        // 계정 활성화 확인 — 신청 후 관리자 승인 전인 계정
        if (!user.isEnabled()) {
            throw new BadCredentialsException("관리자 승인 대기 중인 계정입니다");
        }

        // 비밀번호 확인
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            handleFailedLogin(user);
            throw new BadCredentialsException("잘못된 사용자명 또는 비밀번호입니다");
        }

        // 로그인 성공 - 실패 카운터 초기화
        resetFailedAttempts(user);

        // JWT 토큰 생성
        String accessToken = jwtTokenService.generateAccessToken(user);
        String refreshToken = jwtTokenService.generateRefreshToken(user);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenService.getAccessTokenExpiresIn())
                .user(LoginResponse.UserInfo.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .build())
                .build();
    }

    public User register(RegisterRequest request) {
        return userService.registerUser(request);
    }

    private void handleFailedLogin(User user) {
        int newFailedAttempts = user.getFailedAttempt() + 1;
        userRepository.updateFailedAttempt(newFailedAttempts, user.getUsername());

        if (newFailedAttempts >= MAX_FAILED_ATTEMPTS) {
            lockUser(user);
            log.warn("사용자 계정 잠금: {}", user.getUsername());
        }
    }

    private void lockUser(User user) {
        userRepository.lockUser(user.getUsername(), false, LocalDateTime.now());
    }

    private void unlockUser(User user) {
        userRepository.unlockUser(user.getUsername());
    }

    private void resetFailedAttempts(User user) {
        if (user.getFailedAttempt() > 0) {
            userRepository.updateFailedAttempt(0, user.getUsername());
        }
    }

    private boolean isLockTimeExpired(User user) {
        if (user.getLockTime() == null) {
            return true;
        }
        return user.getLockTime().plusMinutes(LOCK_TIME_DURATION_MINUTES).isBefore(LocalDateTime.now());
    }
}
