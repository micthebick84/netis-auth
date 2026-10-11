package com.example.authserver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesRegex;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 로그인 화면 — 2026-10-11 디자인 승인(B안: NETIS 분할 히어로, 버튼 "로그인").
 * 템플릿 마크업·정적 자산·상태 메시지를 가드한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginPageTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("로그인 페이지는 NETIS 분할 히어로 구조로 렌더링된다 (예전 보라 템플릿 아님)")
    void loginPage_rendersNetisSplitHero() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>로그인 - NETIS</title>")))
                .andExpect(content().string(containsString("/css/login.css")))
                .andExpect(content().string(containsString("class=\"login-hero\"")))
                .andExpect(content().string(containsString("/images/logo.png")))
                .andExpect(content().string(containsString("Network Intelligent System")))
                .andExpect(content().string(containsString("통합관제")))
                .andExpect(content().string(containsString("운영관리")))
                .andExpect(content().string(containsString("class=\"login-card\"")))
                .andExpect(content().string(containsString("/images/logo-login.png")))
                .andExpect(content().string(containsString("name=\"username\"")))
                .andExpect(content().string(containsString("name=\"password\"")))
                .andExpect(content().string(containsString("회원가입")))
                .andExpect(content().string(not(containsString("Auth Server"))))
                .andExpect(content().string(not(containsString("#667eea"))));
    }

    @Test
    @DisplayName("제출 버튼 라벨은 한글 '로그인'이다")
    void loginPage_submitButtonIsKorean() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(matchesRegex("(?s).*<button[^>]*type=\"submit\"[^>]*>\\s*로그인\\s*</button>.*")));
    }

    @Test
    @DisplayName("상태 메시지 — ?error 는 오류 톤, ?logout 은 성공 톤으로 LoginController 문자열 그대로")
    void loginPage_stateMessages() throws Exception {
        mockMvc.perform(get("/login").param("error", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("alert alert-danger")))
                .andExpect(content().string(containsString("잘못된 사용자명 또는 비밀번호입니다")));
        mockMvc.perform(get("/login").param("locked", ""))
                .andExpect(content().string(containsString("계정이 잠겼습니다. 30분 후에 다시 시도해주세요.")));
        mockMvc.perform(get("/login").param("logout", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("alert alert-success")))
                .andExpect(content().string(containsString("로그아웃되었습니다")));
        mockMvc.perform(get("/login").param("registered", ""))
                .andExpect(content().string(containsString("회원가입이 완료되었습니다. 로그인해주세요.")));
    }

    @Test
    @DisplayName("개발용 OAuth2 옵션 박스는 계속 숨김 상태다")
    void loginPage_devOauthOptionStaysHidden() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(content().string(containsString("oauth2-option")))
                .andExpect(content().string(containsString("style=\"display: none;\"")));
    }

    @Test
    @DisplayName("정적 자산(css·로고·글꼴)은 비로그인으로 받을 수 있다")
    void staticAssets_accessibleAnonymously() throws Exception {
        mockMvc.perform(get("/css/login.css")).andExpect(status().isOk());
        mockMvc.perform(get("/images/logo.png")).andExpect(status().isOk());
        mockMvc.perform(get("/images/logo-login.png")).andExpect(status().isOk());
        mockMvc.perform(get("/fonts/PretendardGOVVariable.woff2")).andExpect(status().isOk());
    }
}
