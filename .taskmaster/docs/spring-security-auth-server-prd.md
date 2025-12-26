# Spring Security 7 Authorization Server PRD

## 1. Project Overview

### 1.1 프로젝트 명
Spring Security 7 OAuth2/OIDC Authorization Server

### 1.2 프로젝트 목적
Spring Security 7을 기반으로 OAuth 2.1 및 OpenID Connect(OIDC) 표준을 준수하는 Authorization Server를 구축한다. 클라이언트 애플리케이션에서 JWT 기반 인증을 수행할 수 있도록 토큰 발급 및 검증 기능을 제공한다.

### 1.3 주요 기술 스택
- **Framework**: Spring Boot 3.x, Spring Security 7
- **Authorization Server**: Spring Authorization Server 1.x
- **Database**: Oracle
- **Authentication**: OAuth 2.1, OpenID Connect 1.0
- **Token**: JWT (JSON Web Token)
- **Build Tool**: Gradle 또는 Maven

### 1.4 핵심 가치
- 표준 준수: OAuth 2.1 및 OIDC 스펙 완전 지원
- 보안 강화: JWT 기반 stateless 인증
- 확장성: 커스텀 로그인 페이지 및 사용자 관리 지원
- 유지보수성: 모듈화된 설계

---

## 2. Core Features

### 2.1 사용자 관리 (User Management)

#### 2.1.1 회원가입
- 사용자 등록 API 제공
- 필수 정보: 사용자명(username), 비밀번호(password), 이메일(email)
- 비밀번호 암호화 (BCrypt)
- 이메일/사용자명 중복 체크
- 가입 완료 후 자동 로그인 또는 로그인 페이지 리다이렉트

#### 2.1.2 사용자 정보 저장
- Oracle DB에 사용자 정보 저장
- 사용자 권한(Role) 관리
- 계정 상태 관리 (활성/비활성/잠금)

### 2.2 인증 (Authentication)

#### 2.2.1 로그인
- Form 기반 로그인 (/login)
- 커스텀 로그인 페이지 지원 (JSP/Thymeleaf)
- Remember-me 기능 (선택)
- 로그인 실패 시 에러 메시지 표시
- 로그인 시도 횟수 제한 (Brute Force 방어)

#### 2.2.2 로그아웃
- 세션 무효화
- 토큰 폐기 (Token Revocation)
- 로그아웃 후 리다이렉트 설정

### 2.3 OAuth 2.1 Authorization Server

#### 2.3.1 지원 Grant Types
- **Authorization Code**: 웹 애플리케이션용
- **Authorization Code with PKCE**: SPA/모바일 앱용
- **Client Credentials**: 서버 간 통신용
- **Refresh Token**: 토큰 갱신용

#### 2.3.2 토큰 관리
- Access Token (JWT 형식)
- Refresh Token
- ID Token (OIDC)
- 토큰 유효기간 설정
- 토큰 폐기 (Revocation) 엔드포인트

#### 2.3.3 클라이언트 등록
- Client ID, Client Secret 관리
- Redirect URI 설정
- 허용 Grant Types 설정
- Scope 설정

### 2.4 OpenID Connect (OIDC)

#### 2.4.1 OIDC 엔드포인트
- Authorization Endpoint
- Token Endpoint
- UserInfo Endpoint
- JWK Set Endpoint
- Discovery Endpoint (/.well-known/openid-configuration)

#### 2.4.2 ID Token Claims
- sub (subject)
- name
- email
- preferred_username
- 커스텀 claims 추가 지원

### 2.5 보안 설정

#### 2.5.1 미인증 사용자 처리
- 보호된 리소스 접근 시 /login 페이지로 리다이렉트
- 인증 후 원래 요청 URL로 리다이렉트

#### 2.5.2 CORS 설정
- 허용 Origin 설정
- 허용 Methods 설정
- Credentials 포함 설정

#### 2.5.3 CSRF 보호
- Form 로그인에서 CSRF 토큰 사용
- API 엔드포인트는 선택적 비활성화

---

## 3. User Stories

### US-001: 사용자 회원가입
**As a** 신규 사용자  
**I want to** 계정을 생성할 수 있기를  
**So that** 시스템에 로그인하여 서비스를 이용할 수 있다

**Acceptance Criteria:**
- 회원가입 폼에서 username, password, email 입력
- 중복된 username/email 입력 시 에러 메시지 표시
- 비밀번호는 암호화되어 저장
- 가입 성공 시 로그인 페이지로 이동

### US-002: 사용자 로그인
**As a** 등록된 사용자  
**I want to** username과 password로 로그인할 수 있기를  
**So that** 인증된 서비스에 접근할 수 있다

**Acceptance Criteria:**
- /login 페이지에서 자격 증명 입력
- 로그인 성공 시 원래 요청 URL 또는 기본 페이지로 이동
- 로그인 실패 시 에러 메시지 표시
- 5회 연속 실패 시 계정 잠금

### US-003: OAuth2 토큰 발급
**As a** 클라이언트 애플리케이션  
**I want to** Authorization Code로 Access Token을 발급받을 수 있기를  
**So that** API 리소스에 접근할 수 있다

**Acceptance Criteria:**
- /oauth2/authorize 엔드포인트로 인가 코드 요청
- 사용자 동의 후 redirect_uri로 code 전달
- /oauth2/token 엔드포인트로 토큰 교환
- JWT 형식의 Access Token 반환

### US-004: 미인증 접근 처리
**As a** 미인증 사용자  
**I want to** 보호된 리소스 접근 시 로그인 페이지로 안내되기를  
**So that** 인증 후 원래 요청한 페이지로 이동할 수 있다

**Acceptance Criteria:**
- 인증 필요 페이지 접근 시 /login으로 리다이렉트
- 로그인 성공 후 원래 요청 URL로 리다이렉트
- 세션 만료 시에도 동일하게 처리

### US-005: JWT 토큰 검증
**As a** Resource Server  
**I want to** JWT Access Token을 검증할 수 있기를  
**So that** 인가된 요청만 처리할 수 있다

**Acceptance Criteria:**
- JWK Set 엔드포인트에서 공개키 조회
- JWT 서명 검증
- 토큰 만료 시간 검증
- Scope/권한 검증

---

## 4. Technical Requirements

### 4.1 Database Schema (Oracle)

```sql
-- 사용자 테이블
CREATE TABLE users (
    id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username VARCHAR2(50) NOT NULL UNIQUE,
    password VARCHAR2(255) NOT NULL,
    email VARCHAR2(100) NOT NULL UNIQUE,
    enabled NUMBER(1) DEFAULT 1,
    account_non_locked NUMBER(1) DEFAULT 1,
    failed_attempt NUMBER DEFAULT 0,
    lock_time TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 권한 테이블
CREATE TABLE authorities (
    id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id NUMBER NOT NULL,
    authority VARCHAR2(50) NOT NULL,
    CONSTRAINT fk_authorities_users FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uk_user_authority UNIQUE (user_id, authority)
);

-- OAuth2 클라이언트 등록 테이블
CREATE TABLE oauth2_registered_client (
    id VARCHAR2(100) PRIMARY KEY,
    client_id VARCHAR2(100) NOT NULL UNIQUE,
    client_id_issued_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    client_secret VARCHAR2(255),
    client_secret_expires_at TIMESTAMP,
    client_name VARCHAR2(200) NOT NULL,
    client_authentication_methods VARCHAR2(1000) NOT NULL,
    authorization_grant_types VARCHAR2(1000) NOT NULL,
    redirect_uris VARCHAR2(1000),
    post_logout_redirect_uris VARCHAR2(1000),
    scopes VARCHAR2(1000) NOT NULL,
    client_settings VARCHAR2(2000) NOT NULL,
    token_settings VARCHAR2(2000) NOT NULL
);

-- OAuth2 인가 테이블
CREATE TABLE oauth2_authorization (
    id VARCHAR2(100) PRIMARY KEY,
    registered_client_id VARCHAR2(100) NOT NULL,
    principal_name VARCHAR2(200) NOT NULL,
    authorization_grant_type VARCHAR2(100) NOT NULL,
    authorized_scopes VARCHAR2(1000),
    attributes CLOB,
    state VARCHAR2(500),
    authorization_code_value CLOB,
    authorization_code_issued_at TIMESTAMP,
    authorization_code_expires_at TIMESTAMP,
    authorization_code_metadata CLOB,
    access_token_value CLOB,
    access_token_issued_at TIMESTAMP,
    access_token_expires_at TIMESTAMP,
    access_token_metadata CLOB,
    access_token_type VARCHAR2(100),
    access_token_scopes VARCHAR2(1000),
    oidc_id_token_value CLOB,
    oidc_id_token_issued_at TIMESTAMP,
    oidc_id_token_expires_at TIMESTAMP,
    oidc_id_token_metadata CLOB,
    refresh_token_value CLOB,
    refresh_token_issued_at TIMESTAMP,
    refresh_token_expires_at TIMESTAMP,
    refresh_token_metadata CLOB,
    user_code_value CLOB,
    user_code_issued_at TIMESTAMP,
    user_code_expires_at TIMESTAMP,
    user_code_metadata CLOB,
    device_code_value CLOB,
    device_code_issued_at TIMESTAMP,
    device_code_expires_at TIMESTAMP,
    device_code_metadata CLOB
);

-- OAuth2 인가 동의 테이블
CREATE TABLE oauth2_authorization_consent (
    registered_client_id VARCHAR2(100) NOT NULL,
    principal_name VARCHAR2(200) NOT NULL,
    authorities VARCHAR2(1000) NOT NULL,
    PRIMARY KEY (registered_client_id, principal_name)
);
```

### 4.2 API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /login | 로그인 페이지 |
| POST | /login | 로그인 처리 |
| POST | /logout | 로그아웃 처리 |
| GET | /register | 회원가입 페이지 |
| POST | /register | 회원가입 처리 |
| GET | /oauth2/authorize | 인가 요청 |
| POST | /oauth2/token | 토큰 발급 |
| POST | /oauth2/revoke | 토큰 폐기 |
| POST | /oauth2/introspect | 토큰 검사 |
| GET | /oauth2/jwks | JWK Set |
| GET | /userinfo | 사용자 정보 (OIDC) |
| GET | /.well-known/openid-configuration | OIDC Discovery |

### 4.3 Configuration Properties

```yaml
spring:
  datasource:
    url: jdbc:oracle:thin:@localhost:1521:xe
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: oracle.jdbc.OracleDriver
  
  security:
    oauth2:
      authorizationserver:
        issuer: http://localhost:9000

# JWT 설정
jwt:
  access-token-validity: 3600      # 1시간
  refresh-token-validity: 604800   # 7일
  
# 로그인 설정
login:
  max-failed-attempts: 5
  lock-duration-minutes: 30
```

### 4.4 Project Structure

```
src/
├── main/
│   ├── java/
│   │   └── com/example/authserver/
│   │       ├── config/
│   │       │   ├── SecurityConfig.java
│   │       │   ├── AuthorizationServerConfig.java
│   │       │   └── CorsConfig.java
│   │       ├── controller/
│   │       │   ├── LoginController.java
│   │       │   ├── RegisterController.java
│   │       │   └── UserInfoController.java
│   │       ├── service/
│   │       │   ├── UserService.java
│   │       │   ├── CustomUserDetailsService.java
│   │       │   └── TokenCustomizerService.java
│   │       ├── repository/
│   │       │   ├── UserRepository.java
│   │       │   ├── AuthorityRepository.java
│   │       │   └── OAuth2ClientRepository.java
│   │       ├── entity/
│   │       │   ├── User.java
│   │       │   ├── Authority.java
│   │       │   └── OAuth2Client.java
│   │       ├── dto/
│   │       │   ├── RegisterRequest.java
│   │       │   └── UserInfoResponse.java
│   │       └── AuthServerApplication.java
│   └── resources/
│       ├── application.yml
│       ├── static/
│       │   └── css/
│       │       └── login.css
│       └── templates/  (또는 WEB-INF/views/ for JSP)
│           ├── login.html
│           ├── register.html
│           └── consent.html
└── test/
```

---

## 5. Non-Functional Requirements

### 5.1 성능
- 토큰 발급 응답 시간: 500ms 이하
- 동시 인증 처리: 1000 TPS 이상
- DB 커넥션 풀: 최소 10, 최대 50

### 5.2 보안
- 비밀번호: BCrypt (strength 12)
- JWT 서명: RS256 알고리즘
- HTTPS 필수 (운영 환경)
- Client Secret: 암호화 저장
- SQL Injection 방지
- XSS 방지

### 5.3 가용성
- 99.9% Uptime 목표
- 무중단 키 로테이션 지원
- Health Check 엔드포인트 제공

### 5.4 호환성
- Java 17 이상
- Spring Boot 3.2 이상
- Oracle 19c 이상

---

## 6. Success Metrics

| Metric | Target |
|--------|--------|
| 로그인 성공률 | 99.5% 이상 |
| 토큰 발급 성공률 | 99.9% 이상 |
| 평균 응답 시간 | 200ms 이하 |
| 보안 취약점 | Critical 0건 |

---

## 7. Constraints & Assumptions

### 7.1 제약사항
- Oracle DB 사용 필수
- Spring Security 7 호환성 유지
- 기존 사용자 데이터 마이그레이션 고려

### 7.2 가정
- HTTPS 인증서는 별도 구성
- 클라이언트 애플리케이션은 별도 개발
- 메일 서비스(비밀번호 찾기)는 Phase 2에서 구현

---

## 8. Implementation Tasks

### Phase 1: 기본 구조 설정
- [ ] 프로젝트 초기화 (Spring Boot 3.x)
- [ ] Oracle DB 연결 설정
- [ ] 기본 엔티티 및 리포지토리 생성
- [ ] Database 스키마 생성

### Phase 2: 사용자 관리
- [ ] 회원가입 기능 구현
- [ ] 비밀번호 암호화 설정
- [ ] 사용자 조회 서비스 구현

### Phase 3: Form 로그인
- [ ] SecurityConfig 설정
- [ ] 커스텀 로그인 페이지 구현
- [ ] 로그인 실패 처리 (계정 잠금)
- [ ] 미인증 사용자 리다이렉트 설정

### Phase 4: Authorization Server
- [ ] AuthorizationServerConfig 설정
- [ ] 클라이언트 등록 기능
- [ ] Authorization Code Grant 구현
- [ ] PKCE 지원

### Phase 5: JWT & OIDC
- [ ] JWT 토큰 커스터마이징
- [ ] JWK Set 엔드포인트
- [ ] UserInfo 엔드포인트
- [ ] OIDC Discovery 설정

### Phase 6: 보안 강화
- [ ] CORS 설정
- [ ] Rate Limiting
- [ ] 토큰 폐기 기능
- [ ] 감사 로깅

### Phase 7: 테스트 & 문서화
- [ ] 단위 테스트 작성
- [ ] 통합 테스트 작성
- [ ] API 문서화 (Swagger/OpenAPI)
- [ ] 배포 가이드 작성

---

## 9. Dependencies

```gradle
dependencies {
    // Spring Boot
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    
    // Spring Authorization Server
    implementation 'org.springframework.security:spring-security-oauth2-authorization-server:1.3.0'
    
    // Oracle JDBC
    implementation 'com.oracle.database.jdbc:ojdbc11:23.3.0.23.09'
    
    // JWT
    implementation 'io.jsonwebtoken:jjwt-api:0.12.5'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.5'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.5'
    
    // Template Engine (선택: JSP 또는 Thymeleaf)
    implementation 'org.springframework.boot:spring-boot-starter-thymeleaf'
    // 또는 JSP
    // implementation 'org.apache.tomcat.embed:tomcat-embed-jasper'
    // implementation 'jakarta.servlet.jsp.jstl:jakarta.servlet.jsp.jstl-api'
    
    // Lombok
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
    
    // Test
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.security:spring-security-test'
}
```

---

## 10. Glossary

| Term | Definition |
|------|------------|
| OAuth 2.1 | 권한 위임을 위한 개방형 표준 프로토콜 |
| OIDC | OAuth 2.0 위에 구축된 인증 계층 |
| JWT | JSON 기반의 자체 포함 토큰 형식 |
| PKCE | Proof Key for Code Exchange, 공개 클라이언트를 위한 보안 확장 |
| Access Token | 리소스 접근 권한을 나타내는 토큰 |
| Refresh Token | Access Token 갱신을 위한 토큰 |
| ID Token | 사용자 인증 정보를 포함한 JWT |
| JWK | JSON Web Key, 암호화 키의 JSON 표현 |

---

*Document Version: 1.0*  
*Created: 2025-01-XX*  
*Last Updated: 2025-01-XX*
