package com.example.authserver.controller;

import com.example.authserver.dto.RegisterRequest;
import com.example.authserver.entity.User;
import com.example.authserver.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 계정 신청 화면(/register) — 실제 SecurityConfig(가입 허용 기본값에서 permitAll)를 거쳐 검증한다.
 * @WebMvcTest 슬라이스는 SecurityConfig를 불러오지 않아 기본 보안이 401을 냈고, 컨트롤러의
 * OAuth2ClientService 의존성도 없어 컨텍스트가 뜨지 않았다. 다른 MVC 테스트와 같은 test 프로필(H2) 구성.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegisterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Test
    @DisplayName("계정 신청 페이지 표시")
    void showRegistrationForm() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeExists("registerRequest"))
                .andExpect(content().string(containsString("계정 신청")))
                .andExpect(content().string(containsString("@hamonsoft.co.kr")));
    }

    @Test
    @DisplayName("계정 신청 성공 시 로그인 페이지로 리다이렉트")
    void registerUser_Success() throws Exception {
        User user = new User();
        user.setUsername("testuser");
        when(userService.registerUser(any(RegisterRequest.class))).thenReturn(user);

        mockMvc.perform(post("/register")
                        .param("username", "testuser")
                        .param("password", "password123")
                        .param("email", "test@hamonsoft.co.kr")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?requested"));
    }

    @Test
    @DisplayName("유효성 검증 실패 시 계정 신청 페이지 재표시")
    void registerUser_ValidationError() throws Exception {
        mockMvc.perform(post("/register")
                        .param("username", "ab")  // 너무 짧음
                        .param("password", "short")  // 너무 짧음
                        .param("email", "invalid-email")  // 잘못된 형식
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("register"));
    }

    @Test
    @DisplayName("사용자명이 20자를 넘으면 신청 화면을 다시 보여 준다")
    void registerUser_UsernameTooLong() throws Exception {
        mockMvc.perform(post("/register")
                        .param("username", "a".repeat(21))
                        .param("password", "password123")
                        .param("email", "long@hamonsoft.co.kr")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeHasFieldErrors("registerRequest", "username"));
    }

    @Test
    @DisplayName("중복 사용자명으로 가입 시 에러 메시지 표시")
    void registerUser_DuplicateUsername() throws Exception {
        when(userService.registerUser(any(RegisterRequest.class)))
                .thenThrow(new IllegalArgumentException("이미 사용 중인 사용자명입니다"));

        mockMvc.perform(post("/register")
                        .param("username", "existinguser")
                        .param("password", "password123")
                        .param("email", "new@example.com")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeExists("error"));
    }
}
