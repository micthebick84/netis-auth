package com.example.authserver.controller;

import com.example.authserver.dto.RegisterRequest;
import com.example.authserver.entity.User;
import com.example.authserver.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RegisterController.class)
class RegisterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Test
    @DisplayName("회원가입 페이지 표시")
    void showRegistrationForm() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeExists("registerRequest"));
    }

    @Test
    @DisplayName("회원가입 성공 시 로그인 페이지로 리다이렉트")
    void registerUser_Success() throws Exception {
        User user = new User();
        user.setUsername("testuser");
        when(userService.registerUser(any(RegisterRequest.class))).thenReturn(user);

        mockMvc.perform(post("/register")
                        .param("username", "testuser")
                        .param("password", "password123")
                        .param("email", "test@example.com")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered"));
    }

    @Test
    @DisplayName("유효성 검증 실패 시 회원가입 페이지 재표시")
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
