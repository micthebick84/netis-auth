package com.example.authserver.service;

import com.example.authserver.dto.ClientRegistrationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class OAuth2ClientService {

    private final RegisteredClientRepository registeredClientRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    public RegisteredClient registerClient(ClientRegistrationRequest request) {
        // 중복 체크
        if (registeredClientRepository.findByClientId(request.getClientId()) != null) {
            throw new IllegalArgumentException("이미 등록된 클라이언트 ID입니다: " + request.getClientId());
        }

        RegisteredClient.Builder builder = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(request.getClientId())
                .clientSecret(passwordEncoder.encode(request.getClientSecret()))
                .clientName(request.getClientName())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST);

        // Grant Types 설정
        Set<String> grantTypes = request.getGrantTypes();
        if (grantTypes == null || grantTypes.isEmpty()) {
            // 기본값: Authorization Code + Refresh Token
            builder.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                   .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN);
        } else {
            for (String grantType : grantTypes) {
                switch (grantType.toLowerCase()) {
                    case "authorization_code" -> builder.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE);
                    case "refresh_token" -> builder.authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN);
                    case "client_credentials" -> builder.authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS);
                    case "password" -> builder.authorizationGrantType(new AuthorizationGrantType("password"));
                }
            }
        }

        // Redirect URI 설정
        builder.redirectUri(request.getRedirectUri());
        if (request.getPostLogoutRedirectUri() != null && !request.getPostLogoutRedirectUri().isBlank()) {
            builder.postLogoutRedirectUri(request.getPostLogoutRedirectUri());
        }

        // Scopes 설정
        Set<String> scopes = request.getScopes();
        if (scopes == null || scopes.isEmpty()) {
            // 기본 scopes
            builder.scope(OidcScopes.OPENID)
                   .scope(OidcScopes.PROFILE)
                   .scope(OidcScopes.EMAIL);
        } else {
            for (String scope : scopes) {
                builder.scope(scope);
            }
        }

        // Client Settings
        builder.clientSettings(ClientSettings.builder()
                .requireAuthorizationConsent(request.isRequireConsent())
                .requireProofKey(request.isRequireProofKey())
                .build());

        // Token Settings
        builder.tokenSettings(TokenSettings.builder()
                .accessTokenTimeToLive(Duration.ofSeconds(request.getAccessTokenValiditySeconds()))
                .refreshTokenTimeToLive(Duration.ofSeconds(request.getRefreshTokenValiditySeconds()))
                .reuseRefreshTokens(false)
                .idTokenSignatureAlgorithm(SignatureAlgorithm.RS256)
                .build());

        RegisteredClient registeredClient = builder.build();
        registeredClientRepository.save(registeredClient);

        return registeredClient;
    }

    @Transactional(readOnly = true)
    public RegisteredClient findByClientId(String clientId) {
        return registeredClientRepository.findByClientId(clientId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> findAllClients() {
        String sql = "SELECT id, client_id, client_name, client_id_issued_at, " +
                     "authorization_grant_types, scopes, redirect_uris " +
                     "FROM oauth2_registered_client ORDER BY client_id_issued_at DESC";
        return jdbcTemplate.queryForList(sql);
    }

    public void deleteClient(String clientId) {
        RegisteredClient client = registeredClientRepository.findByClientId(clientId);
        if (client == null) {
            throw new IllegalArgumentException("클라이언트를 찾을 수 없습니다: " + clientId);
        }

        // 관련 인가 및 동의 정보도 삭제
        jdbcTemplate.update("DELETE FROM oauth2_authorization WHERE registered_client_id = ?", client.getId());
        jdbcTemplate.update("DELETE FROM oauth2_authorization_consent WHERE registered_client_id = ?", client.getId());
        jdbcTemplate.update("DELETE FROM oauth2_registered_client WHERE id = ?", client.getId());
    }

    public boolean existsByClientId(String clientId) {
        return registeredClientRepository.findByClientId(clientId) != null;
    }
}
