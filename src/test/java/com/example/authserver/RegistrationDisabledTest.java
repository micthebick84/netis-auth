package com.example.authserver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 공개 배포 하드닝 — app.registration-enabled=false (env REGISTRATION_ENABLED=false).
 * 계정 신청 세 경로가 막히고, 로그인 화면에 계정 신청 링크가 나오지 않아야 한다.
 * 기본값(true)일 때의 동작은 LoginPageTest·SecurityConfigTest가 가드한다.
 */
@SpringBootTest(properties = "app.registration-enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegistrationDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("가입 차단 시 /register 는 로그인으로 리다이렉트된다")
    void registerPage_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("가입 차단 시 /oauth/register 는 로그인으로 리다이렉트된다")
    void oauthRegisterPage_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/oauth/register"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("가입 차단 시 /api/auth/register 는 401")
    void registerApi_isUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"blocked\",\"password\":\"Password1!\",\"email\":\"blocked@example.com\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("가입 차단 시 로그인 화면에 계정 신청 링크가 없다")
    void loginPage_hidesRegisterLink() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"login-card\"")))
                .andExpect(content().string(not(containsString("계정 신청"))))
                .andExpect(content().string(not(containsString("href=\"/register\""))));
    }
}
