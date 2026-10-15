# Claude Code Instructions

## Task Master AI Instructions
**Import Task Master's development workflow commands and guidelines, treat as if import is in the main CLAUDE.md file.**
@./.taskmaster/CLAUDE.md

## 로그인 화면 (`src/main/resources/templates/login.html`, 2026-10-11 NETIS 디자인 적용)

- NETIS 디자인 시스템 토큰(포인트 블루 `#246BEB`, Pretendard GOV, 24px 패널)의 분할 히어로 — 2026-10-11 디자인 검토에서 승인(B안, 버튼 "로그인"). 스타일 `static/css/login.css`, 로고 `static/images/logo.png`·`logo-login.png`, 글꼴 `static/fonts/PretendardGOVVariable.woff2`(OFL-1.1, 5.4MB, 설치돼 있으면 `local()` 우선). 정적 경로는 `SecurityConfig`의 permitAll(`/css/**`, `/js/**`, `/images/**`, `/fonts/**`).
- `OAuth2 Authorization Code 발급받기` 옵션 박스는 `style="display: none;"`(운영 숨김). `LoginPageTest`가 마크업·버튼 라벨·상태 메시지 4종·숨김 옵션·정적 자산 익명 접근을 가드한다.
- `spring.thymeleaf.cache=true` 기본이라 템플릿 수정 후 `bootRun` 재시작 필요. `register.html`·`consent.html`은 아직 예전(보라 그라디언트) 스타일.

## 공개 배포 하드닝 스위치 (`application.yml`, 2026-10-12)

기본값은 개발 동작 그대로이고, 공개 배포(예: Windows `auth-win`)는 env로 끈다.

| env | 기본 | 공개 배포 | 효과 |
|---|---|---|---|
| `REGISTRATION_ENABLED` (`app.registration-enabled`) | true | true (승인형 신청) | `true`면 로그인 화면에 "계정 신청" 링크. 신청 계정은 비활성(`enabled=false`)으로 저장되고 관리자가 `/admin/signups`에서 승인(활성화)·거절(삭제). false면 `/register`·`/api/auth/register` denyAll + 링크 숨김. `/oauth/register`는 항상 차단. `RegistrationDisabledTest`·`SignupFlowTest`·`SignupAdminTest`가 가드 |
| `REGISTRATION_ALLOWED_EMAIL_DOMAIN` (`app.registration.allowed-email-domain`) | hamonsoft.co.kr | hamonsoft.co.kr | 신청 이메일 도메인 — 소문자 비교, `@도메인`으로 끝나야 함 |
| `SQL_INIT_MODE` (`spring.sql.init.mode`) | always | never | 기동마다 `data.sql`이 샘플 계정·클라이언트를 다시 넣지 않게. ⚠️ 빈 DB 첫 기동은 always여야 스키마가 생긴다 |
| `SECURITY_LOG_LEVEL` / `AUTHZ_LOG_LEVEL` | DEBUG / TRACE | INFO | DEBUG/TRACE는 토큰·인가 코드를 로그에 남긴다 |
| `JPA_SHOW_SQL` | true | false | |

## 계정 신청·관리 화면 (2026-10-15)

- 신청(`/register`, `/api/auth/register`) = 비활성 계정 + `ROLE_USER`. 사용자명 영문·숫자·`.`·`_`·`-` 4~20자(키릴 등 유사 문자·공백 불가; Symphony `com."user".user_id` VARCHAR(20)). 대기 계정 로그인은 `/login?pending`(실패 횟수 미집계 — Spring이 비밀번호보다 enabled를 먼저 봐서 대기 여부는 비밀번호 없이도 드러남, 의도적 수용).
- `/admin/**`·`/clients/**`는 `ROLE_ADMIN` 전용(`AdminAccessTest`). 홈은 관리자에게만 관리 링크를 보인다.
- 승인된 사용자의 Symphony `com."user"` 행은 commanCenter가 첫 API 요청 때 자동 생성한다.
- 공개 서버에서 켜기 전/운영 수칙:
  - 승인 전 신청자 본인 확인(사내 메신저 등) — 이메일 소유는 확인하지 않으므로, 기존 Symphony 사용자(`com."user"`)와 같은 사용자명으로 신청하면 승인 시 그 사람의 작업·질문을 넘겨받는다. 이미 `com."user"`에 있는 아이디로 온 신청은 특히 확인.
  - 켜기 전 `SELECT username FROM users WHERE NOT enabled` — 기존 비활성 계정이 "신청"으로 보여 승인될 수 있다.
  - Cloudflare 규칙으로 `/register`·`/api/auth/register` POST에 IP당 속도 제한(신청 엔드포인트는 인증 없이 행을 만든다).
- 스펙: `docs/superpowers/specs/2026-10-15-signup-approval-design.md`
