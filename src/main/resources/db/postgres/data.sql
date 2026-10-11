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

-- nuxt_practice 프론트엔드용 OAuth2 클라이언트
-- nuxt.config.ts의 runtimeConfig.public.oauth 설정과 동기화
-- client_secret 평문: secret123
INSERT INTO oauth2_registered_client (
    id,
    client_id,
    client_secret,
    client_name,
    client_authentication_methods,
    authorization_grant_types,
    redirect_uris,
    post_logout_redirect_uris,
    scopes,
    client_settings,
    token_settings
) VALUES (
    'nuxt-app-id',
    'nuxt-app',
    '$2b$12$0gh/fFhmRNx7CmoicDgOJOLV6GmumVPAYvcr3qJmxOg6lpuzvQi7W',
    'Nuxt Frontend Application',
    'client_secret_basic,client_secret_post',
    'authorization_code,refresh_token',
    'http://localhost:3000/auth/callback',
    'http://localhost:3000/login?logout=true,http://localhost:3000/',
    'openid,profile,email',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":false,"settings.client.require-authorization-consent":false}',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":false,"settings.token.id-token-signature-algorithm":["org.springframework.security.oauth2.jose.jws.SignatureAlgorithm","RS256"],"settings.token.access-token-time-to-live":["java.time.Duration",3600.000000000],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration",604800.000000000],"settings.token.authorization-code-time-to-live":["java.time.Duration",300.000000000],"settings.token.device-code-time-to-live":["java.time.Duration",300.000000000]}'
) ON CONFLICT (client_id) DO NOTHING;

-- egov-boot-web 백엔드용 OAuth2 클라이언트
-- application.yml의 spring.security.oauth2.client.registration.netis-auth와 동기화
-- client_secret 평문: secret456
INSERT INTO oauth2_registered_client (
    id,
    client_id,
    client_secret,
    client_name,
    client_authentication_methods,
    authorization_grant_types,
    redirect_uris,
    post_logout_redirect_uris,
    scopes,
    client_settings,
    token_settings
) VALUES (
    'egov-app-id',
    'egov-app',
    '$2b$12$Z0H.V1k2xth2k/5gayNbyuHg1CqjJ0k.YyeBILjG4i1cssK0OI3au',
    'eGov Boot Web Application',
    'client_secret_basic,client_secret_post',
    'authorization_code,refresh_token',
    'http://localhost:8081/login/oauth2/code/netis-auth',
    'http://localhost:8081/,http://localhost:8081/login?logout=true',
    'openid,profile,email',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":false,"settings.client.require-authorization-consent":false}',
    '{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":false,"settings.token.id-token-signature-algorithm":["org.springframework.security.oauth2.jose.jws.SignatureAlgorithm","RS256"],"settings.token.access-token-time-to-live":["java.time.Duration",3600.000000000],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration",604800.000000000],"settings.token.authorization-code-time-to-live":["java.time.Duration",300.000000000],"settings.token.device-code-time-to-live":["java.time.Duration",300.000000000]}'
) ON CONFLICT (client_id) DO NOTHING;
