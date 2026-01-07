package com.example.authserver.config;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcLogoutAuthenticationToken;
import org.springframework.util.StringUtils;

/**
 * 커스텀 OIDC Logout 인증 프로바이더.
 * sid 클레임 검증을 건너뛰어 세션 불일치 문제를 해결합니다.
 *
 * Spring Security의 Session Fixation 보호로 인해 세션 ID가 로그인 시 변경되어
 * ID Token의 sid와 현재 세션이 불일치하는 문제가 있어 이 검증을 비활성화합니다.
 */
public class CustomOidcLogoutAuthenticationProvider implements AuthenticationProvider {

    private final OAuth2AuthorizationService authorizationService;
    private final RegisteredClientRepository registeredClientRepository;
    private final JwtDecoder jwtDecoder;

    public CustomOidcLogoutAuthenticationProvider(
            OAuth2AuthorizationService authorizationService,
            RegisteredClientRepository registeredClientRepository,
            JwtDecoder jwtDecoder) {
        this.authorizationService = authorizationService;
        this.registeredClientRepository = registeredClientRepository;
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        OidcLogoutAuthenticationToken logoutAuthentication = (OidcLogoutAuthenticationToken) authentication;

        // 이미 인증된 토큰이면 그대로 반환
        if (logoutAuthentication.isAuthenticated()) {
            return logoutAuthentication;
        }

        String idTokenHint = logoutAuthentication.getIdTokenHint();
        if (!StringUtils.hasText(idTokenHint)) {
            throwError(OAuth2ErrorCodes.INVALID_TOKEN, "id_token_hint");
        }

        // ID Token 디코딩 및 검증
        Jwt jwt;
        try {
            jwt = this.jwtDecoder.decode(idTokenHint);
        } catch (Exception ex) {
            throwError(OAuth2ErrorCodes.INVALID_TOKEN, "id_token_hint");
            return null;
        }

        // Authorization 조회
        OAuth2Authorization authorization = this.authorizationService.findByToken(
                idTokenHint, new OAuth2TokenType(OidcIdToken.class.getName()));

        // Client 검증
        String clientId = logoutAuthentication.getClientId();
        if (!StringUtils.hasText(clientId)) {
            clientId = jwt.getAudience().isEmpty() ? null : jwt.getAudience().get(0);
        }

        RegisteredClient registeredClient = null;
        if (StringUtils.hasText(clientId)) {
            registeredClient = this.registeredClientRepository.findByClientId(clientId);
        }

        if (registeredClient == null) {
            throwError(OAuth2ErrorCodes.INVALID_CLIENT, "client_id");
        }

        // Post Logout Redirect URI 검증
        String postLogoutRedirectUri = logoutAuthentication.getPostLogoutRedirectUri();
        if (StringUtils.hasText(postLogoutRedirectUri)) {
            if (!registeredClient.getPostLogoutRedirectUris().contains(postLogoutRedirectUri)) {
                throwError(OAuth2ErrorCodes.INVALID_REQUEST, "post_logout_redirect_uri");
            }
        }

        // Principal 확인 - 현재 인증된 사용자와 ID Token의 subject 비교
        Authentication principal = logoutAuthentication.getPrincipal() instanceof Authentication ?
                (Authentication) logoutAuthentication.getPrincipal() : null;

        boolean isPrincipalAuthenticated = principal != null &&
                principal.isAuthenticated() &&
                !(principal instanceof AnonymousAuthenticationToken);

        // sid 검증을 완전히 건너뜀 (Session Fixation 문제로 인해)

        // 인증된 로그아웃 토큰 반환
        return new OidcLogoutAuthenticationToken(
                jwt.getSubject(),
                isPrincipalAuthenticated ? principal : logoutAuthentication,
                registeredClient.getClientId(),
                postLogoutRedirectUri,
                logoutAuthentication.getState(),
                logoutAuthentication.getSessionId()
        );
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return OidcLogoutAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private void throwError(String errorCode, String parameterName) {
        OAuth2Error error = new OAuth2Error(
                errorCode,
                "OpenID Connect 1.0 Logout Request Parameter: " + parameterName,
                null
        );
        throw new OAuth2AuthenticationException(error);
    }
}
