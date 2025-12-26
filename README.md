# Netis Auth - Spring Authorization Server

Spring Authorization Server 기반의 OAuth2 인증 서버입니다.

## 기술 스택

| 항목 | 버전 |
|------|------|
| Java | 21 |
| Spring Boot | 3.4.1 |
| Spring Security | 6.4.2 |
| Spring Authorization Server | 1.4.0 |
| Oracle Database | 23c |
| Gradle | 8.x |

## Dependencies

| 의존성 | 버전 | 설명 |
|--------|------|------|
| spring-boot-starter-web | 3.4.1 | REST API 및 웹 서버 |
| spring-boot-starter-security | 3.4.1 | Spring Security |
| spring-boot-starter-data-jpa | 3.4.1 | JPA/Hibernate ORM |
| spring-boot-starter-validation | 3.4.1 | Bean Validation |
| spring-boot-starter-thymeleaf | 3.4.1 | 서버 사이드 템플릿 엔진 |
| spring-security-oauth2-authorization-server | 1.4.0 | OAuth2 Authorization Server |
| ojdbc11 | 23.3.0.23.09 | Oracle JDBC Driver |
| jjwt-api | 0.12.5 | JWT 토큰 처리 |
| lombok | edge-SNAPSHOT | 보일러플레이트 코드 생성 |

## 프로젝트 빌드 및 실행

### 사전 요구사항

- JDK 21 이상
- Oracle Database 23c (또는 호환 버전)
- Gradle 8.x (Wrapper 포함)

### 1. 데이터베이스 설정

Oracle 데이터베이스에 접속하여 스키마를 생성합니다:

```sql
-- 사용자 생성 (관리자 계정으로)
CREATE USER netis IDENTIFIED BY ntflow;
GRANT CONNECT, RESOURCE TO netis;
GRANT UNLIMITED TABLESPACE TO netis;
```

### 2. 애플리케이션 설정

`src/main/resources/application.yml` 파일에서 데이터베이스 연결 정보를 수정합니다:

```yaml
spring:
  datasource:
    url: jdbc:oracle:thin:@localhost:8521/FREE
    username: netis
    password: ntflow
```

### 3. 빌드

```bash
# Windows
./gradlew.bat build

# Linux/Mac
./gradlew build
```

### 4. 실행

```bash
# Windows
./gradlew.bat bootRun

# Linux/Mac
./gradlew bootRun
```

서버가 `http://localhost:9000`에서 실행됩니다.

### 5. 테스트

```bash
# Windows
./gradlew.bat test

# Linux/Mac
./gradlew test
```

## 데이터베이스 스키마

### users (사용자)

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| id | NUMBER (PK) | 사용자 ID (자동 생성) |
| username | VARCHAR2(50) | 사용자명 (UNIQUE) |
| password | VARCHAR2(255) | 암호화된 비밀번호 |
| email | VARCHAR2(100) | 이메일 (UNIQUE) |
| enabled | NUMBER(1) | 계정 활성화 여부 |
| account_non_locked | NUMBER(1) | 계정 잠금 여부 |
| failed_attempt | NUMBER | 로그인 실패 횟수 |
| lock_time | TIMESTAMP | 계정 잠금 시간 |
| created_at | TIMESTAMP | 생성일시 |
| updated_at | TIMESTAMP | 수정일시 |

### authorities (권한)

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| id | NUMBER (PK) | 권한 ID (자동 생성) |
| user_id | NUMBER (FK) | 사용자 ID |
| authority | VARCHAR2(50) | 권한명 (예: ROLE_USER) |

### oauth2_registered_client (등록된 클라이언트)

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| id | VARCHAR2(100) (PK) | 클라이언트 UUID |
| client_id | VARCHAR2(100) | 클라이언트 ID |
| client_secret | VARCHAR2(255) | 암호화된 클라이언트 시크릿 |
| client_name | VARCHAR2(200) | 클라이언트 이름 |
| client_authentication_methods | VARCHAR2(1000) | 인증 방식 |
| authorization_grant_types | VARCHAR2(1000) | 허용된 Grant Type |
| redirect_uris | VARCHAR2(1000) | 리다이렉트 URI 목록 |
| scopes | VARCHAR2(1000) | 허용된 스코프 |
| client_settings | VARCHAR2(2000) | 클라이언트 설정 (JSON) |
| token_settings | VARCHAR2(2000) | 토큰 설정 (JSON) |

### oauth2_authorization (인가 정보)

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| id | VARCHAR2(100) (PK) | 인가 UUID |
| registered_client_id | VARCHAR2(100) | 클라이언트 ID |
| principal_name | VARCHAR2(200) | 사용자명 |
| authorization_grant_type | VARCHAR2(100) | Grant Type |
| authorized_scopes | VARCHAR2(1000) | 승인된 스코프 |
| attributes | CLOB | 인가 속성 (JSON) |
| state | VARCHAR2(500) | State 값 |
| authorization_code_value | VARCHAR2(4000) | Authorization Code |
| access_token_value | VARCHAR2(4000) | Access Token |
| refresh_token_value | VARCHAR2(4000) | Refresh Token |
| oidc_id_token_value | VARCHAR2(4000) | ID Token |

### oauth2_authorization_consent (동의 정보)

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| registered_client_id | VARCHAR2(100) (PK) | 클라이언트 ID |
| principal_name | VARCHAR2(200) (PK) | 사용자명 |
| authorities | VARCHAR2(1000) | 동의한 권한 목록 |

## OAuth2 인증 절차

### 지원하는 Grant Types

1. **Authorization Code** - 웹 애플리케이션용 (PKCE 지원)
2. **Refresh Token** - 토큰 갱신
3. **Client Credentials** - 서버 간 통신
4. **Password Grant** - 직접 로그인 (레거시)

---

### 1. Authorization Code Flow (권장)

웹 애플리케이션에서 사용하는 표준 OAuth2 인증 방식입니다.

#### Step 1: 인가 요청

브라우저에서 다음 URL로 리다이렉트합니다:

```
GET http://localhost:9000/oauth2/authorize
    ?response_type=code
    &client_id=test-client
    &redirect_uri=http://localhost:8080/authorized
    &scope=openid profile email
    &state=random_state_value
```

| 파라미터 | 필수 | 설명 |
|----------|------|------|
| response_type | O | `code` 고정 |
| client_id | O | 등록된 클라이언트 ID |
| redirect_uri | O | 등록된 리다이렉트 URI |
| scope | O | 요청 스코프 (공백 구분) |
| state | 권장 | CSRF 방지용 랜덤 값 |

#### Step 2: 사용자 로그인

인증 서버의 로그인 페이지가 표시됩니다:
- URL: `http://localhost:9000/login`
- 사용자명과 비밀번호 입력

#### Step 3: 동의 화면

사용자가 권한 부여에 동의합니다:
- 요청된 스코프 확인
- "승인" 또는 "거부" 선택

#### Step 4: Authorization Code 수신

동의 후 redirect_uri로 리다이렉트됩니다:

```
http://localhost:8080/authorized
    ?code=AUTHORIZATION_CODE_VALUE
    &state=random_state_value
```

#### Step 5: 토큰 교환

Authorization Code를 Access Token으로 교환합니다:

```bash
curl -X POST "http://localhost:9000/oauth2/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -u "test-client:test-secret" \
  -d "grant_type=authorization_code" \
  -d "code=AUTHORIZATION_CODE_VALUE" \
  -d "redirect_uri=http://localhost:8080/authorized"
```

#### 응답

```json
{
  "access_token": "eyJraWQiOi...",
  "refresh_token": "wJ-t7M96BI...",
  "id_token": "eyJraWQiOi...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "openid profile email"
}
```

---

### 2. Refresh Token Flow

Access Token 만료 시 Refresh Token으로 새 토큰을 발급받습니다.

```bash
curl -X POST "http://localhost:9000/oauth2/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -u "test-client:test-secret" \
  -d "grant_type=refresh_token" \
  -d "refresh_token=REFRESH_TOKEN_VALUE"
```

#### 응답

```json
{
  "access_token": "eyJraWQiOi...",
  "refresh_token": "NEW_REFRESH_TOKEN",
  "id_token": "eyJraWQiOi...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "openid profile email"
}
```

> **참고**: Refresh Token Rotation이 적용되어 매번 새로운 refresh_token이 발급됩니다.

---

### 3. Client Credentials Flow

서버 간 통신 시 사용합니다 (사용자 컨텍스트 없음).

```bash
curl -X POST "http://localhost:9000/oauth2/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -u "test-client:test-secret" \
  -d "grant_type=client_credentials" \
  -d "scope=read write"
```

---

### 4. Password Grant Flow (레거시)

직접 사용자 자격 증명으로 토큰을 발급받습니다.

> **주의**: OAuth 2.1에서 deprecated된 방식입니다. 레거시 시스템 마이그레이션 용도로만 사용하세요.

```bash
curl -X POST "http://localhost:9000/oauth2/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -u "password-app:password-secret" \
  -d "grant_type=password" \
  -d "username=testuser" \
  -d "password=password123"
```

---

## API 엔드포인트

### 인증 API

| 메소드 | URL | 설명 |
|--------|-----|------|
| POST | `/api/auth/register` | 회원가입 |
| POST | `/api/auth/login` | 로그인 (JWT 토큰 발급) |
| GET | `/api/auth/me` | 현재 사용자 정보 조회 |

### OAuth2 엔드포인트

| 메소드 | URL | 설명 |
|--------|-----|------|
| GET | `/oauth2/authorize` | 인가 요청 |
| POST | `/oauth2/token` | 토큰 발급/갱신 |
| POST | `/oauth2/revoke` | 토큰 폐기 |
| GET | `/oauth2/jwks` | JWK Set 조회 |
| GET | `/.well-known/openid-configuration` | OIDC Discovery |
| GET | `/userinfo` | 사용자 정보 (OIDC) |

### 웹 페이지

| URL | 설명 |
|-----|------|
| `/login` | 로그인 페이지 |
| `/register` | 회원가입 페이지 |
| `/` | 홈 페이지 |

---

## JWT 토큰 구조

### Access Token Claims

| Claim | 설명 |
|-------|------|
| sub | 사용자명 (Subject) |
| aud | 클라이언트 ID (Audience) |
| iss | 발급자 (http://localhost:9000) |
| exp | 만료 시간 |
| iat | 발급 시간 |
| scope | 승인된 스코프 |
| user_id | 사용자 ID |
| username | 사용자명 |
| email | 이메일 |
| authorities | 권한 목록 |

### ID Token Claims (OIDC)

| Claim | 설명 |
|-------|------|
| sub | 사용자명 |
| name | 이름 |
| email | 이메일 |
| email_verified | 이메일 인증 여부 |
| preferred_username | 선호 사용자명 |

---

## 기본 테스트 계정

서버 시작 시 다음 클라이언트가 자동 등록됩니다:

### test-client

| 항목 | 값 |
|------|-----|
| Client ID | test-client |
| Client Secret | test-secret |
| Grant Types | authorization_code, refresh_token, client_credentials |
| Redirect URIs | http://localhost:8080/authorized |
| Scopes | openid, profile, email, read, write |

### spa-client (PKCE)

| 항목 | 값 |
|------|-----|
| Client ID | spa-client |
| Client Secret | (없음 - Public Client) |
| Grant Types | authorization_code, refresh_token |
| PKCE | 필수 |

---

## 보안 설정

- 비밀번호: BCrypt 암호화
- 토큰 서명: RS256 (RSA)
- Access Token 유효기간: 1시간
- Refresh Token 유효기간: 7일
- Refresh Token Rotation: 활성화
- CORS: 설정 가능

---

## 문제 해결

### Oracle CLOB 비교 오류

`ORA-22848` 오류 발생 시 `SchemaInitializer`가 자동으로 테이블을 VARCHAR2로 재생성합니다.

### Jackson 직렬화 오류

`java.lang.Long not in allowlist` 오류는 `CustomUserDetails`의 `id` 필드가 primitive `long`으로 설정되어 해결됩니다.

---

## 라이선스

MIT License
