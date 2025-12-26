-- =====================================================
-- Auth Server Database Schema for Oracle
-- =====================================================

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
    CONSTRAINT fk_authorities_users FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_user_authority UNIQUE (user_id, authority)
);

-- OAuth2 등록 클라이언트 테이블
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

-- =====================================================
-- 인덱스 생성
-- =====================================================
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_authorities_user_id ON authorities(user_id);
CREATE INDEX idx_oauth2_auth_client_id ON oauth2_authorization(registered_client_id);
CREATE INDEX idx_oauth2_auth_principal ON oauth2_authorization(principal_name);
CREATE INDEX idx_oauth2_auth_state ON oauth2_authorization(state);
