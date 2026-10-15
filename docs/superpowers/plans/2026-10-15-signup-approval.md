# 관리자 승인형 계정 신청 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** netis-auth 로그인 화면에 "계정 신청"을 열되, 신청 계정은 관리자 승인 전까지 로그인할 수 없게 하고, 승인된 사용자가 Symphony(commanCenter)를 바로 쓸 수 있게 한다.

**Architecture:** netis-auth는 기존 `users.enabled`를 대기 플래그로 쓴다(신청=false, 승인=true, 거절=행 삭제). 관리자 전용 `/admin/signups` 화면에서 승인·거절하고, `/admin/**`·`/clients/**`를 `ROLE_ADMIN`으로 막는다. commanCenter는 `/api/**` 인증 뒤 필터에서 `com."user"` 행이 없으면 만든다(프로세스 캐시로 username당 1회).

**Tech Stack:** Spring Boot 3 / Spring Security 6 / Spring Authorization Server / Thymeleaf / JPA(H2 테스트) — netis-auth. Spring Boot / JdbcTemplate / Testcontainers — commanCenter. JDK 21.

**Spec:** `docs/superpowers/specs/2026-10-15-signup-approval-design.md` (netis-auth 레포)

## Global Constraints

- 허용 이메일 도메인: 설정 `app.registration.allowed-email-domain`, 기본 `hamonsoft.co.kr`. 비교는 소문자로, `"@" + 도메인`으로 끝나야 통과.
- 도메인 거부 메시지(정확히): `@hamonsoft.co.kr 이메일만 신청할 수 있습니다` (기본 도메인일 때 — 코드는 `"@" + domain + " 이메일만 신청할 수 있습니다"`).
- 사용자명 길이: 4~20자, 메시지 `사용자명은 4~20자여야 합니다`.
- 로그인 화면 문구(정확히):
  - 링크: `계정이 없으신가요? <a href="/register">계정 신청</a>`
  - `?requested` (성공 톤): `계정 신청이 접수되었습니다. 관리자 승인 후 로그인할 수 있습니다.`
  - `?pending` (오류 톤): `관리자 승인 대기 중인 계정입니다.`
- API 신청 응답 메시지: `계정 신청이 접수되었습니다. 관리자 승인 후 로그인할 수 있습니다`. API 로그인 대기 계정 메시지: `관리자 승인 대기 중인 계정입니다`.
- `REGISTRATION_ENABLED` 스위치는 유지 — false면 `/register`·`/api/auth/register` 차단과 링크 숨김(기존 동작). `/oauth/register`는 스위치와 무관하게 항상 차단.
- commanCenter `com."user"` 자동 생성: `INSERT … ON CONFLICT (user_id) DO NOTHING`, `password`=`!netis-auth`, `user_name`=username, 20자 초과 username은 생성하지 않음. 실패해도 요청은 계속.
- 작성자: netis-auth는 `git -c user.name=khlee84 -c user.email=khlee84@hamonsoft.co.kr commit`, 커밋 메시지 끝에 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- netis-auth 테스트는 `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")`(H2). `@WebMvcTest` 슬라이스 쓰지 말 것(SecurityConfig를 안 불러 401).
- Gradle 실행: `export JAVA_HOME=C:/Users/mic/.jdks/jdk-21.0.12.1+1` 후 각 레포의 `./gradlew`.

## Review Focus

1. 대소문자 섞인 사내 주소(`Kim@HamonSoft.co.kr`)는 통과해야 하고, 비슷한 도메인(`x@hamonsoft.co.kr.evil.com`, `x@evilhamonsoft.co.kr`)은 막혀야 한다 → Task 1 단위 테스트.
2. 비밀번호를 틀린 대기 계정도 `?pending`으로 가고 실패 횟수가 올라가지 않아야 한다(잠금으로 번지지 않게) → Task 3 통합 테스트.
3. 이미 승인된(활성) 계정 id로 거절 요청이 와도 절대 삭제되면 안 된다(오래된 화면·이중 클릭) → Task 5 통합 테스트.
4. 거절된 사용자명·이메일로 다시 신청할 수 있어야 한다 → Task 5 통합 테스트.
5. commanCenter: 20자 초과 username·DB 오류에도 API 요청이 실패하지 않아야 하고, 같은 username은 두 번째부터 DB를 치지 않아야 한다 → Task 6 단위 테스트.

## File Structure

netis-auth (worktree `C:/Users/mic/NetisMaker/.worktrees/signup-approval`, 브랜치 `feature/signup-approval`):

| 파일 | 역할 |
|---|---|
| `src/main/java/com/example/authserver/service/UserService.java` | 신청(대기 생성·도메인 검사), 대기 목록·승인·거절 |
| `src/main/java/com/example/authserver/repository/UserRepository.java` | `findByEnabledFalseOrderByCreatedAtAsc` 추가 |
| `src/main/java/com/example/authserver/dto/RegisterRequest.java` | 사용자명 4~20자 |
| `src/main/java/com/example/authserver/controller/RegisterController.java` | `/register`만 남김, `?requested` |
| `src/main/java/com/example/authserver/controller/LoginController.java` | `requested`/`pending` 메시지 |
| `src/main/java/com/example/authserver/controller/AuthApiController.java` | 신청 응답 메시지 |
| `src/main/java/com/example/authserver/service/AuthService.java` | 대기 계정 API 로그인 메시지 |
| `src/main/java/com/example/authserver/config/CustomAuthenticationFailureHandler.java` | `DisabledException` → `?pending` |
| `src/main/java/com/example/authserver/config/SecurityConfig.java` | `/oauth/register` 상시 차단, `/admin/**`·`/clients/**` ADMIN |
| `src/main/java/com/example/authserver/controller/HomeController.java` | `isAdmin` 모델 |
| `src/main/java/com/example/authserver/controller/SignupAdminController.java` (신규) | 승인 화면 |
| `src/main/resources/templates/admin-signups.html` (신규) | 승인 화면 템플릿 |
| `src/main/resources/templates/{login,register,home}.html` | 문구·링크 |
| `src/main/resources/templates/oauth-register.html` | 삭제 |
| `src/main/resources/application.yml` | `app.registration.allowed-email-domain` |
| `CLAUDE.md` | 스위치 표·승인 흐름 문서화 |
| 테스트: `service/UserServiceTest`, `controller/RegisterControllerTest`, `LoginPageTest`, `RegistrationDisabledTest`, `SecurityConfigTest`, 신규 `SignupFlowTest`, `AdminAccessTest`, `SignupAdminTest` | |

commanCenter (worktree `C:/Users/mic/NetisMaker/commanCenter/.claude/worktrees/user-jit`, 브랜치 `feature/user-jit-provisioning`):

| 파일 | 역할 |
|---|---|
| `src/main/java/com/hamonsoft/netismaker/service/UserProvisioner.java` (신규) | `com."user"` 없으면 INSERT, 캐시 |
| `src/main/java/com/hamonsoft/netismaker/config/UserProvisioningFilter.java` (신규) | JWT 인증 뒤 `ensureUser` 호출 |
| `src/main/java/com/hamonsoft/netismaker/config/SecurityConfig.java` | api 체인에 필터 연결 |
| 테스트: 신규 `service/UserProvisionerTest`(단위), `service/UserProvisionerIntegrationTest`(Testcontainers), `controller/MeControllerTest`(필터 연결), `TaskControllerSseResolverTest`·`ArtifactDownloadControllerTest`(`@MockBean UserProvisioner`) | |
| `CLAUDE.md` | 환경 메모 갱신(`com."user"` 자동 생성) |

---

### Task 1: 신청 = 대기 계정 + 사내 도메인 검사 (netis-auth)

**Files:**
- Modify: `src/main/java/com/example/authserver/service/UserService.java`
- Modify: `src/main/java/com/example/authserver/dto/RegisterRequest.java`
- Modify: `src/main/resources/application.yml`
- Test: `src/test/java/com/example/authserver/service/UserServiceTest.java`

**Interfaces:**
- Produces: `UserService(UserRepository, PasswordEncoder, @Value("${app.registration.allowed-email-domain:hamonsoft.co.kr}") String allowedEmailDomain)` 생성자. `User registerUser(RegisterRequest)` — 저장된 사용자는 `enabled=false`, `ROLE_USER` 1개. 도메인 위반 시 `IllegalArgumentException`.

- [ ] **Step 1: 테스트를 새 동작으로 바꾼다**

`UserServiceTest`에서 `@InjectMocks`를 지우고 직접 생성한다(생성자에 String 인자가 생겨 Mockito가 주입하지 못함). 기존 `registerRequest` 이메일을 사내 주소로 바꾸고, 성공 테스트의 `isEnabled()` 기대를 `isFalse()`로 바꾼다. 그리고 도메인 테스트를 추가한다.

```java
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
```

추가 import: `org.junit.jupiter.params.ParameterizedTest`, `org.junit.jupiter.params.provider.ValueSource`, `static org.mockito.Mockito.never`. `org.mockito.InjectMocks` import는 지운다. `registerUser_DuplicateEmail_ThrowsException`의 `when(userRepository.existsByEmail("test@example.com"))`를 `"test@hamonsoft.co.kr"`로 바꾼다. (`junit-jupiter-params`는 `spring-boot-starter-test`에 포함.)

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests "com.example.authserver.service.UserServiceTest"`
Expected: 컴파일 실패(`UserService` 생성자 인자 3개 없음).

- [ ] **Step 3: 구현**

`UserService` — `@RequiredArgsConstructor`를 지우고 명시 생성자:

```java
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
        if (!request.getEmail().toLowerCase(Locale.ROOT).endsWith("@" + allowedEmailDomain)) {
            throw new IllegalArgumentException("@" + allowedEmailDomain + " 이메일만 신청할 수 있습니다");
        }
        // 중복 체크
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("이미 사용 중인 사용자명입니다");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setEnabled(false);
        user.setAccountNonLocked(true);

        // 기본 권한 부여
        Authority authority = new Authority("ROLE_USER");
        user.addAuthority(authority);

        return userRepository.save(user);
    }
```
import `org.springframework.beans.factory.annotation.Value`, `java.util.Locale`; `lombok.RequiredArgsConstructor` import 제거. 나머지 메서드는 그대로.

`RegisterRequest` 사용자명:
```java
    @NotBlank(message = "사용자명은 필수입니다")
    @Size(min = 4, max = 20, message = "사용자명은 4~20자여야 합니다")
    private String username;
```

`application.yml`의 `app:` 블록(`registration-enabled` 줄 아래)에 추가:
```yaml
  registration:
    # 계정 신청 허용 이메일 도메인 (소문자 비교, "@도메인"으로 끝나야 함)
    allowed-email-domain: ${REGISTRATION_ALLOWED_EMAIL_DOMAIN:hamonsoft.co.kr}
```
(먼저 `app:` 블록 모양을 열어 확인하고 들여쓰기를 맞출 것 — `registration-enabled`와 같은 레벨.)

- [ ] **Step 4: 통과 확인**

Run: `./gradlew test --tests "com.example.authserver.service.UserServiceTest"`
Expected: PASS (기존 5 + 대소문자 1 + 도메인 파라미터 4 = 10건, 실패 0).

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/example/authserver/service/UserService.java src/main/java/com/example/authserver/dto/RegisterRequest.java src/main/resources/application.yml src/test/java/com/example/authserver/service/UserServiceTest.java
git -c user.name=khlee84 -c user.email=khlee84@hamonsoft.co.kr commit -m "feat(signup): 신청 계정은 승인 대기(비활성)로, 사내 이메일 도메인만 허용

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: 신청 화면·로그인 문구·API 응답, `/oauth/register` 제거 (netis-auth)

**Files:**
- Modify: `src/main/java/com/example/authserver/controller/RegisterController.java`
- Delete: `src/main/resources/templates/oauth-register.html`
- Modify: `src/main/java/com/example/authserver/controller/LoginController.java`
- Modify: `src/main/java/com/example/authserver/controller/AuthApiController.java:64-80`
- Modify: `src/main/java/com/example/authserver/config/SecurityConfig.java:70-78`
- Modify: `src/main/resources/templates/login.html:64`, `src/main/resources/templates/register.html`
- Test: `src/test/java/com/example/authserver/controller/RegisterControllerTest.java`, `src/test/java/com/example/authserver/LoginPageTest.java`, `src/test/java/com/example/authserver/RegistrationDisabledTest.java`, `src/test/java/com/example/authserver/SecurityConfigTest.java`, Create `src/test/java/com/example/authserver/SignupFlowTest.java`

**Interfaces:**
- Consumes: Task 1 `UserService.registerUser` (비활성 저장, 도메인 검사).
- Produces: `/register` 성공 → `redirect:/login?requested`. `LoginController`가 `requested`/`pending` 파라미터를 메시지로. `SignupFlowTest` 클래스(이후 Task 3이 테스트를 추가) — 정리 규칙: 테스트 사용자명은 `t_`로 시작, `@AfterEach`에서 삭제.

- [ ] **Step 1: 실패하는 테스트 작성**

`RegisterControllerTest`:
- `registerUser_Success`: 이메일 param `test@hamonsoft.co.kr`, 기대 `redirectedUrl("/login?requested")`.
- `showRegistrationForm`에 `.andExpect(content().string(containsString("계정 신청")))`와 `.andExpect(content().string(containsString("@hamonsoft.co.kr")))` 추가 (`static org.hamcrest.Matchers.containsString` import).
- 신규:
```java
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
```

`LoginPageTest`:
- `loginPage_rendersNetisSplitHero`의 `containsString("회원가입")`을 `containsString("계정 신청")`으로 바꾸고 `.andExpect(content().string(not(containsString("회원가입"))))` 추가.
- `loginPage_stateMessages`의 `registered` 블록을 다음으로 교체:
```java
        mockMvc.perform(get("/login").param("requested", ""))
                .andExpect(content().string(containsString("alert alert-success")))
                .andExpect(content().string(containsString("계정 신청이 접수되었습니다. 관리자 승인 후 로그인할 수 있습니다.")));
        mockMvc.perform(get("/login").param("pending", ""))
                .andExpect(content().string(containsString("alert alert-danger")))
                .andExpect(content().string(containsString("관리자 승인 대기 중인 계정입니다.")));
```
- `@DisplayName`의 "상태 메시지" 설명을 `?requested 는 성공 톤, ?pending 은 오류 톤`까지 포함하도록 갱신.

`RegistrationDisabledTest.loginPage_hidesRegisterLink`: `not(containsString("회원가입"))`를 `not(containsString("계정 신청"))`으로. 클래스 주석의 "회원가입"을 "계정 신청"으로.

`SecurityConfigTest`의 "회원가입 페이지는 누구나 접근 가능" `@DisplayName`을 "계정 신청 페이지는 누구나 접근 가능"으로(동작은 그대로).

신규 `SignupFlowTest` (실제 `UserService`·H2):
```java
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
```
(`ApiResponse`의 메시지 필드 이름을 먼저 확인: `src/main/java/com/example/authserver/dto/ApiResponse.java` — 필드가 `message`가 아니면 jsonPath를 그 이름으로.)

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests "com.example.authserver.controller.RegisterControllerTest" --tests "com.example.authserver.LoginPageTest" --tests "com.example.authserver.RegistrationDisabledTest" --tests "com.example.authserver.SignupFlowTest"`
Expected: FAIL — `?registered` 리다이렉트, "회원가입" 문구, API 메시지, `/oauth/register` 200 등.

- [ ] **Step 3: 구현**

`RegisterController`: `/oauth/register` GET·POST 핸들러 두 개와 `OAuth2ClientService` 필드·관련 import(`RegisteredClient`, `RequestParam`, `UriComponentsBuilder`, `URLEncoder`, `StandardCharsets`)를 지운다. `registerUser` 성공 리다이렉트를 `"redirect:/login?requested"`로. 클래스 Javadoc 추가:
```java
/**
 * 계정 신청(/register). 신청 계정은 비활성으로 저장되고 관리자가 /admin/signups에서 승인해야 로그인할 수 있다.
 */
```

`oauth-register.html` 삭제: `git rm src/main/resources/templates/oauth-register.html`.

`LoginController.login`: `registered` 파라미터·분기를 지우고 다음으로:
```java
    @GetMapping("/login")
    public String login(@RequestParam(value = "error", required = false) String error,
                        @RequestParam(value = "logout", required = false) String logout,
                        @RequestParam(value = "requested", required = false) String requested,
                        @RequestParam(value = "pending", required = false) String pending,
                        @RequestParam(value = "locked", required = false) String locked,
                        Model model) {
        if (error != null) {
            model.addAttribute("error", "잘못된 사용자명 또는 비밀번호입니다");
        }
        if (locked != null) {
            model.addAttribute("error", "계정이 잠겼습니다. 30분 후에 다시 시도해주세요.");
        }
        if (pending != null) {
            model.addAttribute("error", "관리자 승인 대기 중인 계정입니다.");
        }
        if (logout != null) {
            model.addAttribute("message", "로그아웃되었습니다");
        }
        if (requested != null) {
            model.addAttribute("message", "계정 신청이 접수되었습니다. 관리자 승인 후 로그인할 수 있습니다.");
        }
        model.addAttribute("registrationEnabled", registrationEnabled);
        return "login";
    }
```

`AuthApiController.register`의 성공 메시지: `ApiResponse.success("계정 신청이 접수되었습니다. 관리자 승인 후 로그인할 수 있습니다", userData)`. Javadoc `사용자 회원가입 API`를 `계정 신청 API (비활성 계정 생성, 관리자 승인 후 로그인)`로.

`SecurityConfig.defaultSecurityFilterChain`의 `authorizeHttpRequests`:
```java
            .authorizeHttpRequests(auth -> {
                // 신청 직후 바로 인가 흐름으로 넘기는 경로 — 승인형 신청과 맞지 않아 항상 막는다.
                auth.requestMatchers("/oauth/register").denyAll();
                if (!registrationEnabled) {
                    auth.requestMatchers("/register").denyAll();
                }
                auth
                    .requestMatchers("/login", "/register", "/css/**", "/js/**", "/images/**", "/fonts/**", "/error").permitAll()
                    .requestMatchers("/actuator/**").permitAll()
                    .anyRequest().authenticated();
            })
```

`login.html` 64행:
```html
        <p class="register-link" th:if="${registrationEnabled}">계정이 없으신가요? <a th:href="@{/register}">계정 신청</a></p>
```

`register.html`:
- `<title>회원가입 - Auth Server</title>` → `<title>계정 신청 - Auth Server</title>`
- `<h1>회원가입</h1>` → `<h1>계정 신청</h1>`
- 사용자명 placeholder `4자 이상 입력하세요` → `4~20자로 입력하세요`
- 이메일 input placeholder `example@email.com` → `name@hamonsoft.co.kr`, 그리고 이메일 input 바로 아래(오류 div 앞)에:
```html
                <div class="field-hint">@hamonsoft.co.kr 주소만 신청할 수 있습니다. 관리자 승인 후 로그인할 수 있습니다.</div>
```
- `<style>`의 `.error-message` 규칙 앞에:
```css
        .field-hint {
            color: #666;
            font-size: 13px;
            margin-top: 5px;
        }
```
- 버튼 `가입하기` → `신청하기`

- [ ] **Step 4: 통과 확인**

Run: Step 2와 같은 명령.
Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
git add -A src/main src/test
git -c user.name=khlee84 -c user.email=khlee84@hamonsoft.co.kr commit -m "feat(signup): 계정 신청 화면·로그인 안내 문구, /oauth/register 제거

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: 승인 대기 계정 로그인 처리 (netis-auth)

**Files:**
- Modify: `src/main/java/com/example/authserver/config/CustomAuthenticationFailureHandler.java`
- Modify: `src/main/java/com/example/authserver/service/AuthService.java:45-48`
- Test: `src/test/java/com/example/authserver/SignupFlowTest.java`

**Interfaces:**
- Consumes: Task 2 `SignupFlowTest`(정리 규칙 `t_` 접두사), `/login?pending` 메시지.
- Produces: 폼 로그인 `DisabledException` → `/login?pending`(실패 횟수 변화 없음). API 로그인 대기 계정 → 401 `관리자 승인 대기 중인 계정입니다`.

- [ ] **Step 1: 실패하는 테스트 추가 (`SignupFlowTest`)**

필드·헬퍼 추가:
```java
    @Autowired
    PasswordEncoder passwordEncoder;

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
```
테스트:
```java
    @Test
    @DisplayName("대기 계정 폼 로그인 → /login?pending, 실패 횟수 그대로")
    void pendingUser_formLogin_redirectsToPending() throws Exception {
        saveUser("t_pend1", false);

        mockMvc.perform(formLogin("/login").user("t_pend1").password("Password1!"))
                .andExpect(redirectedUrl("/login?pending"))
                .andExpect(unauthenticated());

        assertThat(userRepository.findByUsername("t_pend1").orElseThrow().getFailedAttempt()).isZero();
    }

    @Test
    @DisplayName("대기 계정은 비밀번호가 틀려도 ?pending — 실패 횟수가 쌓여 잠기지 않는다")
    void pendingUser_wrongPassword_doesNotCountOrLock() throws Exception {
        saveUser("t_pend2", false);

        for (int i = 0; i < 6; i++) {
            mockMvc.perform(formLogin("/login").user("t_pend2").password("wrong-password"))
                    .andExpect(redirectedUrl("/login?pending"));
        }

        User after = userRepository.findByUsername("t_pend2").orElseThrow();
        assertThat(after.getFailedAttempt()).isZero();
        assertThat(after.isAccountNonLocked()).isTrue();
    }

    @Test
    @DisplayName("활성 계정은 기존처럼 로그인된다")
    void activeUser_formLogin_authenticates() throws Exception {
        saveUser("t_act1", true);

        mockMvc.perform(formLogin("/login").user("t_act1").password("Password1!"))
                .andExpect(authenticated().withUsername("t_act1"));
    }

    @Test
    @DisplayName("대기 계정 API 로그인 → 401 + 승인 대기 메시지")
    void pendingUser_apiLogin_isUnauthorizedWithMessage() throws Exception {
        saveUser("t_pend3", false);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"t_pend3\",\"password\":\"Password1!\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("관리자 승인 대기 중인 계정입니다"));
    }
```
import 추가: `com.example.authserver.entity.Authority`, `org.springframework.security.crypto.password.PasswordEncoder`, `static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin`, `static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated`, `static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated`. (`LoginRequest` 필드가 `username`/`password`인지 `dto/LoginRequest.java`에서 확인.)

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests "com.example.authserver.SignupFlowTest"`
Expected: FAIL — 대기 계정이 `/login?error`로 가고 실패 횟수 증가, API 메시지 "비활성화된 계정입니다".

- [ ] **Step 3: 구현**

`CustomAuthenticationFailureHandler.onAuthenticationFailure` 맨 앞:
```java
        // 승인 대기(비활성) 계정 — 실패 횟수를 올리지 않는다(Spring은 비밀번호보다 enabled를 먼저 본다).
        if (exception instanceof DisabledException) {
            response.sendRedirect("/login?pending");
            return;
        }
```
import `org.springframework.security.authentication.DisabledException`.

`AuthService.login`의 활성 확인:
```java
        // 계정 활성화 확인 — 신청 후 관리자 승인 전인 계정
        if (!user.isEnabled()) {
            throw new BadCredentialsException("관리자 승인 대기 중인 계정입니다");
        }
```

- [ ] **Step 4: 통과 확인**

Run: `./gradlew test --tests "com.example.authserver.SignupFlowTest"`
Expected: PASS. 만약 `activeUser_formLogin_authenticates`가 성공 핸들러 때문에 실패하면(세션 속성 등) 리다이렉트 여부만 보지 말고 `authenticated()` 매처 결과를 확인 — `CustomAuthenticationSuccessHandler`는 바꾸지 않는다.

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/example/authserver/config/CustomAuthenticationFailureHandler.java src/main/java/com/example/authserver/service/AuthService.java src/test/java/com/example/authserver/SignupFlowTest.java
git -c user.name=khlee84 -c user.email=khlee84@hamonsoft.co.kr commit -m "feat(signup): 승인 대기 계정 로그인 시 안내, 실패 횟수 미집계

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: 관리자 경로 권한 + 홈 관리자 링크 (netis-auth)

**Files:**
- Modify: `src/main/java/com/example/authserver/config/SecurityConfig.java` (Task 2에서 고친 `authorizeHttpRequests`)
- Modify: `src/main/java/com/example/authserver/controller/HomeController.java`
- Modify: `src/main/resources/templates/home.html:107-109`
- Test: Create `src/test/java/com/example/authserver/AdminAccessTest.java`

**Interfaces:**
- Produces: `/admin/**`·`/clients/**`는 `ROLE_ADMIN`만. 홈 모델 `isAdmin`(boolean). 홈의 관리자 링크 `href="/admin/signups"`(텍스트 `가입 신청 관리`)와 `href="/clients"`.

- [ ] **Step 1: 실패하는 테스트 작성**

```java
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
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests "com.example.authserver.AdminAccessTest"`
Expected: FAIL — `user_forbidden`(지금 `/clients` 200·`/admin/signups` 404), 홈 링크 테스트.

- [ ] **Step 3: 구현**

`SecurityConfig`의 `auth` 체인에서 `.requestMatchers("/actuator/**").permitAll()` 다음, `.anyRequest()` 앞에:
```java
                    // 계정 신청이 열리면 일반 사용자도 로그인한다 — 관리 화면은 관리자만.
                    .requestMatchers("/admin/**", "/clients/**").hasRole("ADMIN")
```

`HomeController.home`:
```java
    @GetMapping("/")
    public String home(@AuthenticationPrincipal CustomUserDetails userDetails,
                       Authentication authentication,
                       Model model) {
        if (userDetails != null) {
            model.addAttribute("username", userDetails.getUsername());
            model.addAttribute("email", userDetails.getEmail());
            model.addAttribute("authorities", userDetails.getAuthorities());
        }
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        model.addAttribute("isAdmin", isAdmin);
        return "home";
    }
```
import `org.springframework.security.core.Authentication`.

`home.html`의 `nav-links` 블록:
```html
        <div class="nav-links" th:if="${isAdmin}">
            <a th:href="@{/admin/signups}">가입 신청 관리</a>
            <a th:href="@{/clients}">OAuth2 클라이언트 관리</a>
        </div>
```

- [ ] **Step 4: 통과 확인 (전체 회귀 포함)**

Run: `./gradlew test --tests "com.example.authserver.AdminAccessTest" --tests "com.example.authserver.SecurityConfigTest"`
Expected: PASS. (`/admin/signups` 관리자 200은 Task 5에서 화면이 생긴 뒤 검증.)

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/example/authserver/config/SecurityConfig.java src/main/java/com/example/authserver/controller/HomeController.java src/main/resources/templates/home.html src/test/java/com/example/authserver/AdminAccessTest.java
git -c user.name=khlee84 -c user.email=khlee84@hamonsoft.co.kr commit -m "feat(security): 관리 화면(/admin, /clients)을 ROLE_ADMIN 전용으로, 홈 관리자 링크

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: 가입 신청 승인 화면 `/admin/signups` + 문서 (netis-auth)

**Files:**
- Modify: `src/main/java/com/example/authserver/repository/UserRepository.java`
- Modify: `src/main/java/com/example/authserver/service/UserService.java`
- Create: `src/main/java/com/example/authserver/controller/SignupAdminController.java`
- Create: `src/main/resources/templates/admin-signups.html`
- Modify: `CLAUDE.md`
- Test: Create `src/test/java/com/example/authserver/SignupAdminTest.java`

**Interfaces:**
- Consumes: Task 1 `UserService` 생성자, Task 4 `/admin/**` ADMIN 보호.
- Produces: `UserRepository.findByEnabledFalseOrderByCreatedAtAsc(): List<User>`; `UserService.listPendingSignups(): List<User>`, `approveSignup(Long id): Optional<User>`, `rejectSignup(Long id): Optional<User>` — 대상이 비활성일 때만 처리, 아니면 `Optional.empty()`.

- [ ] **Step 1: 실패하는 테스트 작성**

```java
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
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests "com.example.authserver.SignupAdminTest"`
Expected: FAIL — `/admin/signups` 404.

- [ ] **Step 3: 구현**

`UserRepository`에 추가(import `java.util.List`):
```java
    /** 승인 대기(비활성) 계정 — 신청 순. */
    List<User> findByEnabledFalseOrderByCreatedAtAsc();
```

`UserService`에 추가(import `java.util.List`, `java.util.Optional`):
```java
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
```

`SignupAdminController`:
```java
package com.example.authserver.controller;

import com.example.authserver.entity.User;
import com.example.authserver.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.Optional;

/**
 * 계정 신청 승인 화면 (ROLE_ADMIN — SecurityConfig의 /admin/**).
 * 신청 계정은 비활성으로 저장되고, 승인 = 활성화, 거절 = 삭제(같은 이름으로 재신청 가능).
 */
@Controller
@RequestMapping("/admin/signups")
@RequiredArgsConstructor
@Slf4j
public class SignupAdminController {

    static final String NOT_PENDING = "대기 중인 신청이 아닙니다 (이미 처리되었을 수 있습니다)";

    private final UserService userService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("pending", userService.listPendingSignups());
        return "admin-signups";
    }

    @PostMapping("/{id}/approve")
    public String approve(@PathVariable Long id, Principal admin, RedirectAttributes redirect) {
        Optional<User> done = userService.approveSignup(id);
        done.ifPresent(u -> log.info("계정 신청 승인: {} (관리자 {})", u.getUsername(), admin.getName()));
        return result(done, "%s 계정을 승인했습니다", redirect);
    }

    @PostMapping("/{id}/reject")
    public String reject(@PathVariable Long id, Principal admin, RedirectAttributes redirect) {
        Optional<User> done = userService.rejectSignup(id);
        done.ifPresent(u -> log.info("계정 신청 거절: {} (관리자 {})", u.getUsername(), admin.getName()));
        return result(done, "%s 신청을 거절했습니다", redirect);
    }

    private String result(Optional<User> done, String messageFormat, RedirectAttributes redirect) {
        if (done.isPresent()) {
            redirect.addFlashAttribute("message", messageFormat.formatted(done.get().getUsername()));
        } else {
            redirect.addFlashAttribute("error", NOT_PENDING);
        }
        return "redirect:/admin/signups";
    }
}
```

`admin-signups.html` (home.html과 같은 단순 카드 톤, 거절은 확인 대화상자):
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" lang="ko">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>가입 신청 관리 - Auth Server</title>
    <style>
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #f5f6f8; margin: 0; padding: 24px 16px; }
        .container { max-width: 760px; margin: 0 auto; background: #fff; border-radius: 12px; padding: 24px; box-shadow: 0 2px 12px rgba(0,0,0,0.06); }
        h1 { font-size: 22px; margin: 0 0 16px; }
        .alert { padding: 10px 14px; border-radius: 8px; margin-bottom: 16px; }
        .alert-success { background: #e8f5e9; color: #1b5e20; }
        .alert-danger { background: #fdecea; color: #b71c1c; }
        table { width: 100%; border-collapse: collapse; }
        th, td { text-align: left; padding: 10px 8px; border-bottom: 1px solid #eee; font-size: 14px; }
        td.actions { white-space: nowrap; }
        td.actions form { display: inline; }
        button { border: 0; border-radius: 6px; padding: 6px 12px; cursor: pointer; font-size: 13px; }
        .approve { background: #246BEB; color: #fff; }
        .reject { background: #eee; color: #333; margin-left: 4px; }
        .empty { color: #777; padding: 16px 0; }
        .back { display: inline-block; margin-top: 16px; font-size: 14px; }
        @media (max-width: 600px) { th.created, td.created { display: none; } }
    </style>
</head>
<body>
<div class="container">
    <h1>가입 신청 관리</h1>

    <div th:if="${message}" class="alert alert-success" role="status" th:text="${message}"></div>
    <div th:if="${error}" class="alert alert-danger" role="alert" th:text="${error}"></div>

    <p class="empty" th:if="${#lists.isEmpty(pending)}">대기 중인 신청이 없습니다.</p>

    <table th:unless="${#lists.isEmpty(pending)}">
        <thead>
        <tr><th>사용자명</th><th>이메일</th><th class="created">신청 시각</th><th></th></tr>
        </thead>
        <tbody>
        <tr th:each="u : ${pending}">
            <td th:text="${u.username}">username</td>
            <td th:text="${u.email}">email</td>
            <td class="created" th:text="${u.createdAt != null ? #temporals.format(u.createdAt, 'yyyy-MM-dd HH:mm') : '-'}">2026-10-15 10:00</td>
            <td class="actions">
                <form th:action="@{/admin/signups/{id}/approve(id=${u.id})}" method="post">
                    <button type="submit" class="approve">승인</button>
                </form>
                <!-- 사용자명은 형식 제한이 없어 JS 문자열에 직접 넣지 않고 data 속성으로 넘긴다 -->
                <form th:action="@{/admin/signups/{id}/reject(id=${u.id})}" method="post"
                      class="reject-form" th:data-username="${u.username}">
                    <button type="submit" class="reject">거절</button>
                </form>
            </td>
        </tr>
        </tbody>
    </table>

    <a class="back" th:href="@{/}">← 홈으로</a>
</div>
<script>
    document.querySelectorAll('.reject-form').forEach(function (form) {
        form.addEventListener('submit', function (e) {
            if (!confirm(form.dataset.username + ' 신청을 거절할까요? 계정이 삭제됩니다.')) e.preventDefault();
        });
    });
</script>
</body>
</html>
```

`CLAUDE.md` — "공개 배포 하드닝 스위치" 표의 `REGISTRATION_ENABLED` 행 효과를 다음으로 바꾸고:
`true`면 로그인 화면에 "계정 신청" 링크. 신청 계정은 비활성(`enabled=false`)으로 저장되고 관리자가 `/admin/signups`에서 승인(활성화)·거절(삭제). false면 `/register`·`/api/auth/register` denyAll + 링크 숨김. `/oauth/register`는 항상 차단. `RegistrationDisabledTest`·`SignupFlowTest`·`SignupAdminTest`가 가드
표 아래에 `REGISTRATION_ALLOWED_EMAIL_DOMAIN` 행 추가(기본 `hamonsoft.co.kr`, 신청 이메일 도메인 — 소문자 비교, `@도메인`으로 끝나야 함). 그리고 섹션 추가:
```markdown
## 계정 신청·관리 화면 (2026-10-15)

- 신청(`/register`, `/api/auth/register`) = 비활성 계정 + `ROLE_USER`. 사용자명 4~20자(Symphony `com."user".user_id` VARCHAR(20)). 대기 계정 로그인은 `/login?pending`(실패 횟수 미집계 — Spring이 비밀번호보다 enabled를 먼저 봐서 대기 여부는 비밀번호 없이도 드러남, 의도적 수용).
- `/admin/**`·`/clients/**`는 `ROLE_ADMIN` 전용(`AdminAccessTest`). 홈은 관리자에게만 관리 링크를 보인다.
- 승인된 사용자의 Symphony `com."user"` 행은 commanCenter가 첫 API 요청 때 자동 생성한다.
- 스펙: `docs/superpowers/specs/2026-10-15-signup-approval-design.md`
```

- [ ] **Step 4: 통과 확인 + 전체 회귀**

Run: `./gradlew test --tests "com.example.authserver.SignupAdminTest"` → PASS.
Run: `./gradlew test` → 실패 0 (기준선 26건 + 신규).

- [ ] **Step 5: 커밋**

```bash
git add src/main src/test CLAUDE.md
git -c user.name=khlee84 -c user.email=khlee84@hamonsoft.co.kr commit -m "feat(signup): 가입 신청 승인 화면(/admin/signups)과 문서

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Symphony `com."user"` 자동 생성 (commanCenter)

작업 위치: `cd /c/Users/mic/NetisMaker/commanCenter && git fetch -q origin && git worktree add .claude/worktrees/user-jit -b feature/user-jit-provisioning origin/main` 후 그 worktree에서(본 체크아웃은 라이브라 테스트 금지). 이 PC는 Docker가 없어 Testcontainers 테스트는 건너뛰어진다 — CI에서 돈다.

**Files:**
- Create: `src/main/java/com/hamonsoft/netismaker/service/UserProvisioner.java`
- Create: `src/main/java/com/hamonsoft/netismaker/config/UserProvisioningFilter.java`
- Modify: `src/main/java/com/hamonsoft/netismaker/config/SecurityConfig.java`
- Modify: `CLAUDE.md` (환경 메모의 `confirm` 전제조건 항목)
- Test: Create `src/test/java/com/hamonsoft/netismaker/service/UserProvisionerTest.java`, Create `src/test/java/com/hamonsoft/netismaker/service/UserProvisionerIntegrationTest.java`, Modify `src/test/java/com/hamonsoft/netismaker/controller/MeControllerTest.java`, `TaskControllerSseResolverTest.java`, `ArtifactDownloadControllerTest.java`

**Interfaces:**
- Produces: `UserProvisioner(JdbcTemplate)` `@Component @Profile("api")`, `void ensureUser(String username, String email)`. `UserProvisioningFilter(UserProvisioner)` — `OncePerRequestFilter`, **`@Component` 아님**(서블릿 필터로 전역 등록되지 않게 SecurityConfig에서 `new`).

- [ ] **Step 1: 실패하는 테스트 작성**

`UserProvisionerTest`(단위):
```java
package com.hamonsoft.netismaker.service;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UserProvisionerTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final UserProvisioner provisioner = new UserProvisioner(jdbc);

    @Test
    void inserts_once_per_username() {
        provisioner.ensureUser("newbie", "newbie@hamonsoft.co.kr");
        provisioner.ensureUser("newbie", "newbie@hamonsoft.co.kr");

        verify(jdbc, times(1)).update(UserProvisioner.INSERT_SQL,
                "newbie", "newbie", UserProvisioner.NO_LOGIN_PASSWORD, "newbie@hamonsoft.co.kr");
    }

    @Test
    void skips_username_longer_than_user_id_column() {
        provisioner.ensureUser("a".repeat(21), "x@hamonsoft.co.kr");

        verifyNoInteractions(jdbc);
    }

    @Test
    void skips_blank_username() {
        provisioner.ensureUser(" ", null);

        verifyNoInteractions(jdbc);
    }

    @Test
    void db_failure_does_not_throw_and_retries_next_time() {
        when(jdbc.update(anyString(), any(Object[].class)))
                .thenThrow(new DataAccessResourceFailureException("down"))
                .thenReturn(1);

        assertThatCode(() -> provisioner.ensureUser("flaky", null)).doesNotThrowAnyException();
        provisioner.ensureUser("flaky", null);

        verify(jdbc, times(2)).update(anyString(), any(Object[].class));
    }
}
```
(`JdbcTemplate.update(String, Object...)`는 가변 인자 — 스텁·검증의 `any(Object[].class)`가 Mockito 버전에서 가변 인자와 안 맞으면 `any()` 하나로 바꾼다. `eq`·`anyString` import는 쓰지 않으면 지운다.)

`UserProvisionerIntegrationTest`(Testcontainers, CI):
```java
package com.hamonsoft.netismaker.service;

import com.hamonsoft.netismaker.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** com."user" 자동 생성 SQL — 실제 PostgreSQL(init-test-schema.sql의 com."user"). */
@EnabledIfEnvironmentVariable(named = "RUN_TESTCONTAINERS", matches = "true")
@SpringBootTest
@ContextConfiguration(initializers = TestcontainersConfig.class)
class UserProvisionerIntegrationTest {

    @Autowired UserProvisioner provisioner;
    @Autowired JdbcTemplate jdbc;

    @Test
    void creates_missing_user_row() {
        provisioner.ensureUser("jit_new1", "jit_new1@hamonsoft.co.kr");

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT user_name, password, email FROM com.\"user\" WHERE user_id = 'jit_new1'");
        assertThat(row.get("user_name")).isEqualTo("jit_new1");
        assertThat(row.get("password")).isEqualTo(UserProvisioner.NO_LOGIN_PASSWORD);
        assertThat(row.get("email")).isEqualTo("jit_new1@hamonsoft.co.kr");
    }

    @Test
    void keeps_existing_row_untouched() {
        provisioner.ensureUser("user1", "changed@example.com");

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT user_name, password, email FROM com.\"user\" WHERE user_id = 'user1'");
        assertThat(row.get("user_name")).isEqualTo("User 1");
        assertThat(row.get("password")).isEqualTo("x");
        assertThat(row.get("email")).isEqualTo("user1@hamonsoft.co.kr");
    }
}
```

`MeControllerTest`(이미 `@WebMvcTest` + `@Import({SecurityConfig.class, CorsConfig.class, WorkerApiKeyFilter.class})`): 필드 `@MockBean UserProvisioner userProvisioner;` 추가, 그리고 필터 연결 테스트:
```java
    @Test
    void authenticated_api_request_provisions_user() throws Exception {
        mockMvc.perform(get("/api/me").with(jwt().jwt(j -> j
                        .claim("username", "newbie")
                        .claim("email", "newbie@hamonsoft.co.kr"))))
                .andExpect(status().isOk());

        verify(userProvisioner).ensureUser("newbie", "newbie@hamonsoft.co.kr");
    }
```
(파일을 먼저 열어 `mockMvc` 필드 이름·기존 import를 확인하고 맞춘다. 필요 import: `com.hamonsoft.netismaker.service.UserProvisioner`, `org.springframework.boot.test.mock.mockito.MockBean`, `static org.mockito.Mockito.verify`, `static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt`.)

`TaskControllerSseResolverTest`, `ArtifactDownloadControllerTest`: 각각 `@MockBean UserProvisioner userProvisioner;` 필드만 추가(SecurityConfig가 새 의존성을 요구하므로).

- [ ] **Step 2: 실패 확인**

Run: `./gradlew test --tests "com.hamonsoft.netismaker.service.UserProvisionerTest" --tests "com.hamonsoft.netismaker.controller.MeControllerTest"`
Expected: 컴파일 실패(`UserProvisioner` 없음).

- [ ] **Step 3: 구현**

`UserProvisioner`:
```java
package com.hamonsoft.netismaker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * netis-auth에서 승인된 사용자의 com."user" 행을 첫 API 요청 때 만든다.
 *
 * 작업·질문·첨부 테이블이 com."user"(user_id)를 FK로 참조해, 행이 없으면 등록이 500으로 실패한다.
 * com."user"는 netis-backend 공용 테이블 — 기존 행은 건드리지 않고(ON CONFLICT DO NOTHING),
 * password는 NOT NULL이라 로그인에 쓰이지 않는 표식을 넣는다. 확인한 username은 프로세스 안에 기억해
 * 요청마다 DB를 치지 않는다. 실패해도 요청은 계속한다(실제 등록에서 FK 오류로 드러난다).
 */
@Component
@Profile("api")
public class UserProvisioner {

    static final int MAX_USER_ID_LENGTH = 20;
    static final String NO_LOGIN_PASSWORD = "!netis-auth";
    static final String INSERT_SQL = "INSERT INTO com.\"user\" (user_id, user_name, password, email) "
            + "VALUES (?, ?, ?, ?) ON CONFLICT (user_id) DO NOTHING";

    private static final Logger log = LoggerFactory.getLogger(UserProvisioner.class);

    private final JdbcTemplate jdbc;
    private final Set<String> known = ConcurrentHashMap.newKeySet();

    public UserProvisioner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void ensureUser(String username, String email) {
        if (username == null || username.isBlank() || known.contains(username)) return;
        if (username.length() > MAX_USER_ID_LENGTH) {
            log.warn("com.\"user\" 자동 생성 건너뜀 — user_id {}자 초과: {}", MAX_USER_ID_LENGTH, username);
            known.add(username);
            return;
        }
        try {
            jdbc.update(INSERT_SQL, username, username, NO_LOGIN_PASSWORD, email);
            known.add(username);
        } catch (DataAccessException e) {
            log.warn("com.\"user\" 자동 생성 실패 user={}: {}", username, e.getMessage());
        }
    }
}
```

`UserProvisioningFilter`:
```java
package com.hamonsoft.netismaker.config;

import com.hamonsoft.netismaker.controller.AuthContext;
import com.hamonsoft.netismaker.service.UserProvisioner;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * /api/** JWT 인증 뒤 com."user" 행을 보장한다(UserProvisioner).
 * @Component로 두면 서블릿 필터로 전역 등록되므로 SecurityConfig api 체인에서만 생성한다.
 */
public class UserProvisioningFilter extends OncePerRequestFilter {

    private final UserProvisioner provisioner;

    public UserProvisioningFilter(UserProvisioner provisioner) {
        this.provisioner = provisioner;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            try {
                provisioner.ensureUser(AuthContext.requireUserId(jwt), jwt.getToken().getClaimAsString("email"));
            } catch (IllegalStateException ignored) {
                // username/sub/user_id 클레임이 없는 토큰 — 컨트롤러가 같은 예외로 처리한다
            }
        }
        chain.doFilter(request, response);
    }
}
```
(`AuthContext.requireUserId`의 시그니처가 `(JwtAuthenticationToken)` → `String`인지 `controller/AuthContext.java:19`에서 확인.)

`SecurityConfig`: 필드·생성자에 `UserProvisioner userProvisioner` 추가, `apiFilterChain`의 `.oauth2ResourceServer(...)` 뒤에:
```java
                .addFilterAfter(new UserProvisioningFilter(userProvisioner), BearerTokenAuthenticationFilter.class);
```
import `com.hamonsoft.netismaker.service.UserProvisioner`, `org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter`. 클래스 주석의 `/api/**` 줄을 `JWT (netis-auth, ROLE_USER / ROLE_ADMIN) + com."user" 자동 생성(UserProvisioningFilter)`로.

`CLAUDE.md` 환경 메모의 **`confirm`(구현 진행) 전제조건** 항목 끝에 한 문장 추가:
`2026-10-15부터 API가 인증된 첫 요청 때 com."user" 행을 자동으로 만든다(service/UserProvisioner + config/UserProvisioningFilter, password='!netis-auth', 20자 초과 username은 건너뜀) — netis-auth 계정 신청 승인 흐름과 짝.`

- [ ] **Step 4: 통과 확인**

Run: `./gradlew test --tests "com.hamonsoft.netismaker.service.UserProvisionerTest" --tests "com.hamonsoft.netismaker.controller.MeControllerTest" --tests "com.hamonsoft.netismaker.controller.TaskControllerSseResolverTest" --tests "com.hamonsoft.netismaker.controller.ArtifactDownloadControllerTest"` → PASS.
Run: `./gradlew test` → 이 PC에서 원래 실패하는 3건(`AttachmentCleanupJobTest`·`AttachmentStorageTest` 심볼릭 링크)만 실패해야 한다. `UserProvisionerIntegrationTest`는 skip(Testcontainers) — CI에서 확인.

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/hamonsoft/netismaker/service/UserProvisioner.java src/main/java/com/hamonsoft/netismaker/config/UserProvisioningFilter.java src/main/java/com/hamonsoft/netismaker/config/SecurityConfig.java CLAUDE.md src/test/java
git commit -m "feat(auth): 첫 API 요청 때 com.\"user\" 행 자동 생성 (netis-auth 계정 신청 승인과 짝)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

## 병합·배포 (구현 후, 사용자 확인 받고)

1. commanCenter PR → CI 3개 통과 → 병합 → 본 체크아웃 `git pull --ff-only` → API만 재기동(작업 스케줄러 절차).
2. netis-auth PR → 사용자 "병합해줘" → 병합 → 본 체크아웃 반영(wrapper 파일 변경 없음 → `git pull --ff-only`) → auth 재기동.
3. `%USERPROFILE%\netis-maker\public.env`에서 `REGISTRATION_ENABLED=true` → auth 재기동.
4. 실화면 확인: auth-win 로그인 화면 "계정 신청" 링크, 신청 → `?requested`, 대기 로그인 → `?pending`, admin으로 `/admin/signups` 승인, 승인 계정으로 Symphony 로그인 → `com."user"` 행 생성.
