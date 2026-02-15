-- =====================================================
-- Sample Data for Auth Server (PostgreSQL)
-- =====================================================

-- 테스트 사용자 추가 (비밀번호: password123 - BCrypt 인코딩)
INSERT INTO users (username, password, email, enabled, account_non_locked)
VALUES ('admin', '$2a$12$mUkiFJYylARwaxuqkMaGB./uxFui837Wk4yZpYEVXgWIYsZONwlAy', 'admin@example.com', TRUE, TRUE)
ON CONFLICT (username) DO NOTHING;

INSERT INTO users (username, password, email, enabled, account_non_locked)
VALUES ('user', '$2a$12$mUkiFJYylARwaxuqkMaGB./uxFui837Wk4yZpYEVXgWIYsZONwlAy', 'user@example.com', TRUE, TRUE)
ON CONFLICT (username) DO NOTHING;

-- 권한 추가
INSERT INTO authorities (user_id, authority)
SELECT id, 'ROLE_ADMIN' FROM users WHERE username = 'admin'
ON CONFLICT (user_id, authority) DO NOTHING;

INSERT INTO authorities (user_id, authority)
SELECT id, 'ROLE_USER' FROM users WHERE username = 'admin'
ON CONFLICT (user_id, authority) DO NOTHING;

INSERT INTO authorities (user_id, authority)
SELECT id, 'ROLE_USER' FROM users WHERE username = 'user'
ON CONFLICT (user_id, authority) DO NOTHING;

-- 샘플 OAuth2 클라이언트 등록
INSERT INTO oauth2_registered_client (
    id,
    client_id,
    client_secret,
    client_name,
    client_authentication_methods,
    authorization_grant_types,
    redirect_uris,
    scopes,
    client_settings,
    token_settings
) VALUES (
    'sample-client-id',
    'sample-client',
    '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi',
    'Sample Client Application',
    'client_secret_basic,client_secret_post',
    'authorization_code,refresh_token,client_credentials',
    'http://localhost:8080/login/oauth2/code/sample-client',
    'openid,profile,email',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":false,"settings.client.require-authorization-consent":true}',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":true,"settings.token.id-token-signature-algorithm":["org.springframework.security.oauth2.jose.jws.SignatureAlgorithm","RS256"],"settings.token.access-token-time-to-live":["java.time.Duration",3600.000000000],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration",86400.000000000],"settings.token.authorization-code-time-to-live":["java.time.Duration",300.000000000],"settings.token.device-code-time-to-live":["java.time.Duration",300.000000000]}'
) ON CONFLICT (client_id) DO NOTHING;
