package com.example.authserver.service;

import com.example.authserver.dto.RegisterRequest;
import com.example.authserver.entity.User;
import com.example.authserver.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    private RegisterRequest registerRequest;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder, "hamonsoft.co.kr");
        registerRequest = new RegisterRequest();
        registerRequest.setUsername("testuser");
        registerRequest.setPassword("password123");
        registerRequest.setEmail("test@hamonsoft.co.kr");
    }

    @Test
    @DisplayName("신청 성공 — 승인 대기(비활성) 계정으로 저장된다")
    void registerUser_Success() {
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.registerUser(registerRequest);

        assertThat(result.getUsername()).isEqualTo("testuser");
        assertThat(result.getPassword()).isEqualTo("encodedPassword");
        assertThat(result.getEmail()).isEqualTo("test@hamonsoft.co.kr");
        assertThat(result.isEnabled()).isFalse();
        assertThat(result.isAccountNonLocked()).isTrue();
        assertThat(result.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("중복 사용자명으로 회원가입 시 예외 발생")
    void registerUser_DuplicateUsername_ThrowsException() {
        // given
        when(userRepository.existsByUsername("testuser")).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> userService.registerUser(registerRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 사용 중인 사용자명");
    }

    @Test
    @DisplayName("중복 이메일로 회원가입 시 예외 발생")
    void registerUser_DuplicateEmail_ThrowsException() {
        // given
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail("test@hamonsoft.co.kr")).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> userService.registerUser(registerRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 사용 중인 이메일");
    }

    @Test
    @DisplayName("사용자명 존재 여부 확인")
    void existsByUsername() {
        // given
        when(userRepository.existsByUsername("testuser")).thenReturn(true);

        // when & then
        assertThat(userService.existsByUsername("testuser")).isTrue();
    }

    @Test
    @DisplayName("이메일 존재 여부 확인")
    void existsByEmail() {
        // given
        when(userRepository.existsByEmail("test@hamonsoft.co.kr")).thenReturn(true);

        // when & then
        assertThat(userService.existsByEmail("test@hamonsoft.co.kr")).isTrue();
    }

    @Test
    @DisplayName("대소문자가 섞인 사내 주소도 통과한다")
    void registerUser_MixedCaseDomain_Accepted() {
        registerRequest.setEmail("Kim@HamonSoft.co.kr");
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(userService.registerUser(registerRequest).getEmail()).isEqualTo("Kim@HamonSoft.co.kr");
    }

    @ParameterizedTest
    @ValueSource(strings = {"test@example.com", "x@hamonsoft.co.kr.evil.com", "x@evilhamonsoft.co.kr", "hamonsoft.co.kr@gmail.com"})
    @DisplayName("사내 도메인이 아니면 저장하지 않고 거부한다")
    void registerUser_OtherDomain_Rejected(String email) {
        registerRequest.setEmail(email);

        assertThatThrownBy(() -> userService.registerUser(registerRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("@hamonsoft.co.kr 이메일만 신청할 수 있습니다");
        verify(userRepository, never()).save(any(User.class));
    }
}
