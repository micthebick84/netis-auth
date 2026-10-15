package com.example.authserver.service;

import com.example.authserver.dto.RegisterRequest;
import com.example.authserver.entity.Authority;
import com.example.authserver.entity.User;
import com.example.authserver.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String allowedEmailDomain;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       @Value("${app.registration.allowed-email-domain:hamonsoft.co.kr}") String allowedEmailDomain) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.allowedEmailDomain = allowedEmailDomain.toLowerCase(Locale.ROOT);
    }

    /**
     * 계정 신청 — 관리자 승인 전까지 로그인할 수 없는 비활성 계정으로 만든다(/admin/signups에서 승인).
     */
    public User registerUser(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (!email.endsWith("@" + allowedEmailDomain)) {
            throw new IllegalArgumentException("@" + allowedEmailDomain + " 이메일만 신청할 수 있습니다");
        }
        // 중복 체크
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("이미 사용 중인 사용자명입니다");
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(email);
        user.setEnabled(false);
        user.setAccountNonLocked(true);

        // 기본 권한 부여
        Authority authority = new Authority("ROLE_USER");
        user.addAuthority(authority);

        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Transactional(readOnly = true)
    public List<User> listPendingSignups() {
        return userRepository.findByEnabledFalseOrderByCreatedAtAsc();
    }

    /** 대기 중인 신청만 승인한다. 이미 활성이거나 없으면 empty. */
    public Optional<User> approveSignup(Long id) {
        return userRepository.findById(id)
                .filter(u -> !u.isEnabled())
                .map(u -> {
                    u.setEnabled(true);
                    return u;
                });
    }

    /** 대기 중인 신청만 삭제한다(권한 행은 cascade). 활성 계정은 절대 지우지 않는다. */
    public Optional<User> rejectSignup(Long id) {
        return userRepository.findById(id)
                .filter(u -> !u.isEnabled())
                .map(u -> {
                    userRepository.delete(u);
                    return u;
                });
    }
}
