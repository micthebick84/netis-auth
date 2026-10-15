package com.example.authserver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 관리자 화면 접근 — 일반 사용자가 생기므로 /admin/**·/clients/** 는 ROLE_ADMIN 전용. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAccessTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("익명 사용자는 관리자 화면에서 로그인으로 보내진다")
    void anonymous_redirectedToLogin() throws Exception {
        mockMvc.perform(get("/admin/signups"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(get("/clients"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @WithMockUser(username = "plain", roles = "USER")
    @DisplayName("일반 사용자는 관리자 화면에서 403")
    void user_forbidden() throws Exception {
        mockMvc.perform(get("/admin/signups")).andExpect(status().isForbidden());
        mockMvc.perform(get("/clients")).andExpect(status().isForbidden());
        mockMvc.perform(get("/clients/edit/any")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "plain", roles = "USER")
    @DisplayName("일반 사용자 홈에는 관리자 링크가 없다")
    void user_homeHidesAdminLinks() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("가입 신청 관리"))))
                .andExpect(content().string(not(containsString("href=\"/clients\""))));
    }

    @Test
    @WithMockUser(username = "boss", roles = {"ADMIN", "USER"})
    @DisplayName("관리자 홈에는 가입 신청 관리·클라이언트 관리 링크가 있다")
    void admin_homeShowsAdminLinks() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/admin/signups\"")))
                .andExpect(content().string(containsString("가입 신청 관리")))
                .andExpect(content().string(containsString("href=\"/clients\"")));
    }
}
