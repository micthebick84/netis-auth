package com.example.authserver.config;

import com.example.authserver.config.security.CustomUserDetailsModule;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationServerJackson2Module;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Configuration
@RequiredArgsConstructor
public class OAuth2ClientConfig {

    private final PasswordEncoder passwordEncoder;

    @Bean
    public RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcRegisteredClientRepository(jdbcTemplate);
    }

    @Bean
    public OAuth2AuthorizationService authorizationService(JdbcTemplate jdbcTemplate,
                                                            RegisteredClientRepository registeredClientRepository) {
        JdbcOAuth2AuthorizationService authorizationService =
                new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository);

        // CustomUserDetails를 직렬화/역직렬화 할 수 있도록 ObjectMapper 설정
        JdbcOAuth2AuthorizationService.OAuth2AuthorizationRowMapper rowMapper =
                new JdbcOAuth2AuthorizationService.OAuth2AuthorizationRowMapper(registeredClientRepository);

        ObjectMapper objectMapper = new ObjectMapper();
        ClassLoader classLoader = JdbcOAuth2AuthorizationService.class.getClassLoader();
        List<Module> securityModules = SecurityJackson2Modules.getModules(classLoader);
        objectMapper.registerModules(securityModules);
        objectMapper.registerModule(new OAuth2AuthorizationServerJackson2Module());

        // CustomUserDetails를 직렬화 허용 목록에 추가
        objectMapper.registerModule(new CustomUserDetailsModule());

        rowMapper.setObjectMapper(objectMapper);
        authorizationService.setAuthorizationRowMapper(rowMapper);

        // 세션 ID를 추적하는 래퍼로 감싸서 반환
        return new SessionTrackingAuthorizationService(authorizationService);
    }

    @Bean
    public OAuth2AuthorizationConsentService authorizationConsentService(JdbcTemplate jdbcTemplate,
                                                                          RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationConsentService(jdbcTemplate, registeredClientRepository);
    }

    @Bean
    public ApplicationRunner initializeClients(RegisteredClientRepository repository, JdbcTemplate jdbcTemplate) {
        return args -> {
            // nuxt-app 클라이언트의 post_logout_redirect_uris 업데이트 (NULL인 경우)
            updateNuxtAppPostLogoutRedirectUris(jdbcTemplate);

            // 테스트 클라이언트 등록
            if (repository.findByClientId("test-client") == null) {
                RegisteredClient testClient = RegisteredClient.withId(UUID.randomUUID().toString())
                        .clientId("test-client")
                        .clientSecret(passwordEncoder.encode("test-secret"))
                        .clientName("Test Application")
                        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                        .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                        .redirectUri("http://localhost:8080/login/oauth2/code/auth-server")
                        .redirectUri("http://localhost:8080/authorized")
                        .postLogoutRedirectUri("http://localhost:8080/")
                        .scope(OidcScopes.OPENID)
                        .scope(OidcScopes.PROFILE)
                        .scope(OidcScopes.EMAIL)
                        .scope("read")
                        .scope("write")
                        .clientSettings(ClientSettings.builder()
                                .requireAuthorizationConsent(true)
                                .requireProofKey(false)
                                .build())
                        .tokenSettings(TokenSettings.builder()
                                .accessTokenTimeToLive(Duration.ofHours(1))
                                .refreshTokenTimeToLive(Duration.ofDays(7))
                                .reuseRefreshTokens(false)
                                .idTokenSignatureAlgorithm(SignatureAlgorithm.RS256)
                                .build())
                        .build();
                repository.save(testClient);
            }

            // Nuxt Application 클라이언트
            if (repository.findByClientId("nuxt-app") == null) {
                RegisteredClient nuxtClient = RegisteredClient.withId(UUID.randomUUID().toString())
                        .clientId("nuxt-app")
                        .clientSecret(passwordEncoder.encode("secret123"))
                        .clientName("Nuxt Application")
                        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                        .redirectUri("http://localhost:3000/auth/callback")
                        .postLogoutRedirectUri("http://localhost:3000/login?logout=true")
                        .postLogoutRedirectUri("http://localhost:3000")
                        .postLogoutRedirectUri("http://localhost:3000/")
                        .postLogoutRedirectUri("http://localhost:3000/login")
                        .scope(OidcScopes.OPENID)
                        .scope(OidcScopes.PROFILE)
                        .scope(OidcScopes.EMAIL)
                        .clientSettings(ClientSettings.builder()
                                .requireAuthorizationConsent(true)
                                .requireProofKey(false)
                                .build())
                        .tokenSettings(TokenSettings.builder()
                                .accessTokenTimeToLive(Duration.ofHours(1))
                                .refreshTokenTimeToLive(Duration.ofDays(7))
                                .reuseRefreshTokens(false)
                                .idTokenSignatureAlgorithm(SignatureAlgorithm.RS256)
                                .build())
                        .build();
                repository.save(nuxtClient);
            }

            // PKCE 지원 SPA 클라이언트
            if (repository.findByClientId("spa-client") == null) {
                RegisteredClient spaClient = RegisteredClient.withId(UUID.randomUUID().toString())
                        .clientId("spa-client")
                        .clientName("Single Page Application")
                        .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                        .redirectUri("http://localhost:3000/callback")
                        .postLogoutRedirectUri("http://localhost:3000/")
                        .scope(OidcScopes.OPENID)
                        .scope(OidcScopes.PROFILE)
                        .scope(OidcScopes.EMAIL)
                        .clientSettings(ClientSettings.builder()
                                .requireAuthorizationConsent(true)
                                .requireProofKey(true)  // PKCE 필수
                                .build())
                        .tokenSettings(TokenSettings.builder()
                                .accessTokenTimeToLive(Duration.ofMinutes(30))
                                .refreshTokenTimeToLive(Duration.ofDays(1))
                                .reuseRefreshTokens(false)
                                .idTokenSignatureAlgorithm(SignatureAlgorithm.RS256)
                                .build())
                        .build();
                repository.save(spaClient);
            }
        };
    }

    private void updateNuxtAppPostLogoutRedirectUris(JdbcTemplate jdbcTemplate) {
        String checkSql = "SELECT post_logout_redirect_uris FROM oauth2_registered_client WHERE client_id = ?";
        String updateSql = "UPDATE oauth2_registered_client SET post_logout_redirect_uris = ? WHERE client_id = ?";

        try {
            String currentValue = jdbcTemplate.queryForObject(checkSql, String.class, "nuxt-app");
            // 슬래시 있는 버전과 없는 버전 모두 포함
            String postLogoutUris = "http://localhost:3000,http://localhost:3000/,http://localhost:3000/login?logout=true,http://localhost:3000/login";
            // 값이 없거나 http://localhost:3000 (슬래시 없는) 포함되지 않은 경우 업데이트
            if (currentValue == null || currentValue.isBlank() || !currentValue.startsWith("http://localhost:3000,")) {
                int updated = jdbcTemplate.update(updateSql, postLogoutUris, "nuxt-app");
                if (updated > 0) {
                    System.out.println("nuxt-app 클라이언트의 post_logout_redirect_uris가 업데이트되었습니다: " + postLogoutUris);
                }
            }
        } catch (Exception e) {
            // nuxt-app 클라이언트가 없는 경우 무시
        }
    }
}
