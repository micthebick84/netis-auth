package com.example.authserver.service;

import com.example.authserver.dto.RegisterRequest;
import com.example.authserver.entity.User;
import com.example.authserver.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private RegisterRequest registerRequest;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest();
        registerRequest.setUsername("testuser");
        registerRequest.setPassword("password123");
        registerRequest.setEmail("test@example.com");
    }

    @Test
    @DisplayName("회원가입 성공 테스트")
    void registerUser_Success() {
        // given
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return user;
        });

        // when
        User result = userService.registerUser(registerRequest);

        // then
        assertThat(result.getUsername()).isEqualTo("testuser");
        assertThat(result.getPassword()).isEqualTo("encodedPassword");
        assertThat(result.getEmail()).isEqualTo("test@example.com");
        assertThat(result.isEnabled()).isTrue();
        assertThat(result.isAccountNonLocked()).isTrue();
        assertThat(result.getAuthorities()).hasSize(1);

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
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

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
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        // when & then
        assertThat(userService.existsByEmail("test@example.com")).isTrue();
    }
}
