package com.example.authserver;

import com.example.authserver.entity.User;
import com.example.authserver.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 관리자 승인형 계정 신청 — 실제 UserService·SecurityConfig·H2로 끝까지 본다.
 * 테스트 사용자명은 t_ 로 시작하고 매 테스트 뒤 지운다(H2 메모리 DB를 다른 테스트 컨텍스트와 공유).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SignupFlowTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @AfterEach
    void cleanup() {
        userRepository.findAll().stream()
                .filter(u -> u.getUsername().startsWith("t_"))
                .forEach(userRepository::delete);
    }

    @Test
    @DisplayName("폼 신청 → 비활성 계정 저장 + /login?requested")
    void formSignup_createsPendingUser() throws Exception {
        mockMvc.perform(post("/register")
                        .param("username", "t_form1")
                        .param("password", "Password1!")
                        .param("email", "t_form1@hamonsoft.co.kr")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?requested"));

        User saved = userRepository.findByUsername("t_form1").orElseThrow();
        assertThat(saved.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("사내 도메인이 아니면 저장 없이 신청 화면에 오류를 보여 준다")
    void formSignup_otherDomain_showsError() throws Exception {
        mockMvc.perform(post("/register")
                        .param("username", "t_form2")
                        .param("password", "Password1!")
                        .param("email", "t_form2@example.com")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(content().string(containsString("@hamonsoft.co.kr 이메일만 신청할 수 있습니다")));

        assertThat(userRepository.findByUsername("t_form2")).isEmpty();
    }

    @Test
    @DisplayName("API 신청 → 201 + 승인 안내 메시지 + 비활성 저장")
    void apiSignup_createsPendingUser() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"t_api1\",\"password\":\"Password1!\",\"email\":\"t_api1@hamonsoft.co.kr\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("계정 신청이 접수되었습니다. 관리자 승인 후 로그인할 수 있습니다"));

        assertThat(userRepository.findByUsername("t_api1").orElseThrow().isEnabled()).isFalse();
    }

    @Test
    @DisplayName("/oauth/register 는 가입이 켜져 있어도 막힌다")
    void oauthRegister_alwaysBlocked() throws Exception {
        mockMvc.perform(get("/oauth/register"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }
}
