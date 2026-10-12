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
| `REGISTRATION_ENABLED` (`app.registration-enabled`) | true | false | `/register`·`/oauth/register`·`/api/auth/register`를 permitAll보다 먼저 denyAll + 로그인 화면 회원가입 링크 숨김(`LoginController` → `registrationEnabled`). `RegistrationDisabledTest`가 가드 |
| `SQL_INIT_MODE` (`spring.sql.init.mode`) | always | never | 기동마다 `data.sql`이 샘플 계정·클라이언트를 다시 넣지 않게. ⚠️ 빈 DB 첫 기동은 always여야 스키마가 생긴다 |
| `SECURITY_LOG_LEVEL` / `AUTHZ_LOG_LEVEL` | DEBUG / TRACE | INFO | DEBUG/TRACE는 토큰·인가 코드를 로그에 남긴다 |
| `JPA_SHOW_SQL` | true | false | |
