# 관리자 승인형 계정 신청 — 설계

- 날짜: 2026-10-15
- 대상 레포: `netis-auth`(주), `commanCenter`(Symphony, `com."user"` 자동 생성)
- 배경: 공개 배포(auth-win)는 `REGISTRATION_ENABLED=false`라 로그인 화면에 가입 링크가 없다. 그런데 현재 가입은 곧바로 활성 `ROLE_USER` 계정을 만들기 때문에, 켜는 순간 인터넷의 누구나 Symphony(Claude 구독)를 쓸 수 있다.

## 목표

- 공개 로그인 화면에 "계정 신청" 버튼이 보인다.
- 신청 계정은 관리자가 승인하기 전까지 로그인할 수 없다.
- 승인된 사용자는 별도 수동 작업 없이 Symphony에서 작업·질문을 등록할 수 있다.

## 범위 밖

- 신청·승인 알림 메일(관리자가 승인 화면에서 직접 확인).
- 이메일 소유 확인(인증 메일). 도메인 검사만 하므로 남의 사내 주소를 적은 신청은 관리자가 거른다.
- 거절 이력 보관, 계정 비활성화(차단) 기능.

## 결정 사항

| 항목 | 결정 |
|---|---|
| 대기 상태 표현 | 기존 `users.enabled` 재사용 — 신청 = `false`, 승인 = `true`. 스키마 변경 없음 |
| 거절 | 사용자 행과 권한 행 삭제 → 같은 사용자명·이메일로 재신청 가능 |
| 신청 필드 | 사용자명·비밀번호·이메일(현행 그대로) + 이메일은 `@hamonsoft.co.kr` 필수(대소문자 무시) |
| 스위치 | `REGISTRATION_ENABLED` 유지. 켜져 있으면 가입은 **항상** 승인형. auth-win은 `true`로 켠다 |
| 알림 | 없음 |
| Symphony 사용자 | Symphony가 인증 요청 때 `com."user"` 행이 없으면 만든다(JIT) |

## netis-auth 변경

### 1. 신청 처리 (`UserService.registerUser`)

- 이메일 도메인 검사: 소문자로 바꾼 이메일이 `@hamonsoft.co.kr`로 끝나지 않으면 `IllegalArgumentException("@hamonsoft.co.kr 이메일만 신청할 수 있습니다")`. 허용 도메인은 설정값 `app.registration.allowed-email-domain`(기본 `hamonsoft.co.kr`)으로 둔다 — 테스트·다른 배포에서 바꿀 수 있게.
- `user.setEnabled(false)`로 저장. 권한은 지금처럼 `ROLE_USER` 하나.
- `/register`(폼)와 `/api/auth/register`(API)가 같은 서비스를 쓰므로 둘 다 승인형이 된다.
  - 폼: 성공 시 `redirect:/login?requested`.
  - API: 201 응답 메시지를 "계정 신청이 접수되었습니다. 관리자 승인 후 로그인할 수 있습니다"로.

### 2. `/oauth/register` 차단

가입 직후 인가 흐름으로 넘기는 경로라 승인형과 맞지 않는다. `SecurityConfig`에서 `REGISTRATION_ENABLED`와 무관하게 항상 `denyAll`, `RegisterController`의 두 핸들러와 `oauth-register.html` 템플릿은 삭제한다.

### 3. 로그인 화면·실패 처리

- `login.html` 링크 문구: "계정이 없으신가요? **계정 신청**" (`/register`, 기존 `th:if="${registrationEnabled}"` 유지).
- `LoginController` 메시지 추가:
  - `?requested` → (성공 알림) "계정 신청이 접수되었습니다. 관리자 승인 후 로그인할 수 있습니다."
  - `?pending` → (오류 알림) "관리자 승인 대기 중인 계정입니다."
  - 기존 `?registered` 분기는 제거(더 이상 쓰는 곳 없음).
- `CustomAuthenticationFailureHandler`: 예외가 `DisabledException`이면 실패 횟수를 올리지 않고 `/login?pending`으로 보낸다. 나머지는 현행 그대로.
- 알려진 트레이드오프: Spring의 사전 검사는 비밀번호 확인 전에 `enabled`를 보므로, 비밀번호를 몰라도 "승인 대기" 여부가 드러난다(대기 중인 사용자명 존재 확인). 공개 범위가 사내 신청자로 좁고 노출 정보가 대기 여부뿐이라 받아들인다. API 로그인(`AuthService.login`)도 원래 같은 순서다 — 메시지만 "관리자 승인 대기 중인 계정입니다"로 맞춘다.
- `register.html`: 제목·버튼을 "계정 신청"으로, 이메일 안내에 "@hamonsoft.co.kr 주소만 가능, 관리자 승인 후 로그인"을 적는다. 이번 범위에서 디자인은 바꾸지 않는다(기존 보라 스타일 유지).

### 4. 관리자 승인 화면 (`/admin/signups`)

- 새 `SignupAdminController`(`@RequestMapping("/admin/signups")`):
  - `GET` — `enabled=false`인 사용자 목록(사용자명·이메일·신청 시각 `created_at`, 오래된 순). 템플릿 `admin-signups.html`.
  - `POST /{id}/approve` — `enabled=false`인 사용자만 `true`로. 대상이 없거나 이미 활성이면 변경 없이 목록으로(메시지).
  - `POST /{id}/reject` — `enabled=false`인 사용자만 권한 행과 함께 삭제. 활성 사용자는 절대 지우지 않는다.
  - 처리 후 `redirect:/admin/signups?approved=<사용자명>` 등으로 결과 메시지 표시.
- 서비스 메서드는 `UserService`에 `listPendingSignups()`, `approveSignup(id)`, `rejectSignup(id)` 추가. 승인·거절은 로그(`log.info`, 처리한 관리자 이름 포함).
- 홈(`home.html`): `ROLE_ADMIN`일 때만 "가입 신청 관리" 링크와 "OAuth2 클라이언트 관리" 링크를 보여 준다.
- CSRF: 기본 체인은 CSRF가 켜져 있으므로 승인·거절 폼에 Thymeleaf `th:action`으로 토큰이 자동 포함되게 한다.

### 5. 관리자 경로 권한

`SecurityConfig.defaultSecurityFilterChain`에 `requestMatchers("/admin/**", "/clients/**").hasRole("ADMIN")`을 `anyRequest().authenticated()`보다 앞에 둔다. 지금 `/clients`는 로그인한 누구나 열 수 있다 — 일반 사용자가 생기므로 같이 막는다.

## commanCenter 변경 — `com."user"` JIT 생성

- 작업·질문·첨부 등 여러 테이블이 `com."user"(user_id)`를 FK로 참조한다. 행이 없는 사용자가 등록하면 500.
- 새 컴포넌트 `UserProvisioner`(api 프로필): `ensureUser(username, email)` — `INSERT INTO com."user"(user_id, user_name, password, email) VALUES (?, ?, ?, ?) ON CONFLICT (user_id) DO NOTHING`. `password`는 NOT NULL이라 로그인에 쓰이지 않는 표식 문자열(`'!netis-auth'`)을 넣는다. 기존 행은 건드리지 않는다.
- 호출 시점: 인증된 `/api/**` 요청마다 필터로 돌리면 매번 쿼리가 나가므로, **프로세스 안 캐시(이미 확인한 username 집합)** 를 두고 처음 본 username일 때만 실행한다. 위치는 `apiFilterChain`의 bearer 인증 뒤에 붙는 `OncePerRequestFilter`(`UserProvisioningFilter`) — `/api/me`만으로는 localStorage 토큰 복원 경로를 놓칠 수 있어서 필터로 한다.
- `user_id` 컬럼이 `VARCHAR(20)`이다. netis-auth 사용자명은 4~50자라 20자를 넘으면 넣을 수 없다 → netis-auth 신청 검증에서 사용자명을 **4~20자**로 줄인다(기존 계정은 모두 20자 이하). 필터는 20자 초과 username이면 생성하지 않고 경고 로그만 남긴다(요청은 계속).
- 생성 실패(DB 오류)는 경고 로그 후 요청을 계속 진행한다 — 실제 등록에서 FK 오류로 드러난다.

## 배포 순서

1. commanCenter(JIT 생성) 병합·API 재기동.
2. netis-auth 병합·auth 재기동.
3. `public.env`에서 `REGISTRATION_ENABLED=true` → auth 재기동.

1을 먼저 하는 이유: 승인된 첫 사용자가 Symphony에 들어오기 전에 JIT 생성이 준비돼 있어야 한다.

## 테스트

netis-auth(`@SpringBootTest` + `@AutoConfigureMockMvc`, `test` 프로필 H2 관례):
- 신청: 사내 도메인 → `enabled=false` 저장 + `/login?requested`; 다른 도메인·대문자 도메인 처리; 21자 사용자명 거부.
- 대기 계정 로그인 → `/login?pending`, `failed_attempt` 증가 없음.
- 승인 → 로그인 성공; 거절 → 행 삭제 후 같은 이름으로 재신청 가능; 활성 사용자 id로 reject → 삭제 안 됨.
- `/admin/signups`·`/clients`: 익명 → 로그인 리다이렉트, `ROLE_USER` → 403, `ROLE_ADMIN` → 200.
- `/oauth/register` → 항상 차단.
- 로그인 화면: 등록 켜짐 → "계정 신청" 링크, 꺼짐 → 숨김(`RegistrationDisabledTest` 갱신), `?requested`/`?pending` 문구(`LoginPageTest` 갱신).
- API 등록: 201 + 비활성 저장.

commanCenter:
- `UserProvisioner`: 없으면 생성, 있으면 그대로(기존 `password`·`email` 보존), 20자 초과는 건너뜀 — Testcontainers 통합 테스트(CI에서 실행).
- 필터: 같은 username 두 번째 요청은 DB 호출 없음(단위 테스트, mock).
