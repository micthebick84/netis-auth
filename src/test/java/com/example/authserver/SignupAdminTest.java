package com.example.authserver;

import com.example.authserver.entity.Authority;
import com.example.authserver.entity.User;
import com.example.authserver.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 가입 신청 승인 화면 — 실제 UserService·H2. 사용자명 t_ 접두사, 매 테스트 뒤 삭제. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SignupAdminTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @AfterEach
    void cleanup() {
        userRepository.findAll().stream()
                .filter(u -> u.getUsername().startsWith("t_"))
                .forEach(userRepository::delete);
    }

    private User saveUser(String username, boolean enabled) {
        User u = new User();
        u.setUsername(username);
        u.setPassword(passwordEncoder.encode("Password1!"));
        u.setEmail(username + "@hamonsoft.co.kr");
        u.setEnabled(enabled);
        u.setAccountNonLocked(true);
        u.addAuthority(new Authority("ROLE_USER"));
        return userRepository.save(u);
    }

    @Test
    @DisplayName("관리자는 대기 목록을 본다 — 활성 계정은 목록에 없다")
    void list_showsOnlyPending() throws Exception {
        saveUser("t_wait1", false);
        saveUser("t_live1", true);

        mockMvc.perform(get("/admin/signups").with(user("boss").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin-signups"))
                .andExpect(content().string(containsString("t_wait1")))
                .andExpect(content().string(containsString("t_wait1@hamonsoft.co.kr")))
                .andExpect(content().string(not(containsString("t_live1"))));
    }

    @Test
    @DisplayName("승인하면 활성화되고 로그인할 수 있다")
    void approve_enablesAndAllowsLogin() throws Exception {
        User pending = saveUser("t_wait2", false);

        mockMvc.perform(post("/admin/signups/{id}/approve", pending.getId())
                        .with(user("boss").roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/signups"))
                .andExpect(flash().attribute("message", "t_wait2 계정을 승인했습니다"));

        assertThat(userRepository.findByUsername("t_wait2").orElseThrow().isEnabled()).isTrue();
        mockMvc.perform(formLogin("/login").user("t_wait2").password("Password1!"))
                .andExpect(authenticated().withUsername("t_wait2"));
    }

    @Test
    @DisplayName("거절하면 삭제되고 같은 이름·이메일로 다시 신청할 수 있다")
    void reject_deletesAndAllowsReapply() throws Exception {
        User pending = saveUser("t_wait3", false);

        mockMvc.perform(post("/admin/signups/{id}/reject", pending.getId())
                        .with(user("boss").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/signups"))
                .andExpect(flash().attribute("message", "t_wait3 신청을 거절했습니다"));
        assertThat(userRepository.findByUsername("t_wait3")).isEmpty();

        mockMvc.perform(post("/register")
                        .param("username", "t_wait3")
                        .param("password", "Password1!")
                        .param("email", "t_wait3@hamonsoft.co.kr")
                        .with(csrf()))
                .andExpect(redirectedUrl("/login?requested"));
    }

    @Test
    @DisplayName("활성 계정 id로 거절이 와도 지우지 않는다(오래된 화면·이중 클릭)")
    void reject_activeUser_isNoOp() throws Exception {
        User live = saveUser("t_live2", true);

        mockMvc.perform(post("/admin/signups/{id}/reject", live.getId())
                        .with(user("boss").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/signups"))
                .andExpect(flash().attribute("error", "대기 중인 신청이 아닙니다 (이미 처리되었을 수 있습니다)"));

        assertThat(userRepository.findByUsername("t_live2")).isPresent();
    }

    @Test
    @DisplayName("이미 승인된 신청을 다시 승인해도 오류 없이 안내만 한다")
    void approve_twice_isNoOp() throws Exception {
        User pending = saveUser("t_wait4", false);
        mockMvc.perform(post("/admin/signups/{id}/approve", pending.getId())
                .with(user("boss").roles("ADMIN")).with(csrf()));

        mockMvc.perform(post("/admin/signups/{id}/approve", pending.getId())
                        .with(user("boss").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/signups"))
                .andExpect(flash().attribute("error", "대기 중인 신청이 아닙니다 (이미 처리되었을 수 있습니다)"));
    }

    @Test
    @DisplayName("일반 사용자는 승인할 수 없다")
    void approve_byUser_forbidden() throws Exception {
        User pending = saveUser("t_wait5", false);

        mockMvc.perform(post("/admin/signups/{id}/approve", pending.getId())
                        .with(user("plain").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(userRepository.findByUsername("t_wait5").orElseThrow().isEnabled()).isFalse();
    }
}
