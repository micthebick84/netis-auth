package com.example.authserver.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponseType;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * OAuth2AuthorizationService 래퍼로, Authorization 저장 시 현재 세션 ID를 함께 저장합니다.
 * OIDC RP-Initiated Logout에서 sid 클레임 검증을 위해 필요합니다.
 */
public class SessionTrackingAuthorizationService implements OAuth2AuthorizationService {

    public static final String SESSION_ID_ATTRIBUTE = "session_id";

    private final OAuth2AuthorizationService delegate;

    public SessionTrackingAuthorizationService(OAuth2AuthorizationService delegate) {
        this.delegate = delegate;
    }

    @Override
    public void save(OAuth2Authorization authorization) {
        OAuth2Authorization authorizationToSave = authorization;

        // Authorization Code가 있을 때만 세션 ID를 저장 (인증 완료 후)
        // 이렇게 해야 Session Fixation 보호로 변경된 새 세션 ID가 저장됨
        if (authorization.getToken(OAuth2AuthorizationCode.class) != null) {
            String sessionId = getCurrentSessionId();
            if (sessionId != null) {
                // 항상 최신 세션 ID로 업데이트
                authorizationToSave = OAuth2Authorization.from(authorization)
                        .attribute(SESSION_ID_ATTRIBUTE, sessionId)
                        .build();
            }
        }

        delegate.save(authorizationToSave);
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        delegate.remove(authorization);
    }

    @Override
    public OAuth2Authorization findById(String id) {
        return delegate.findById(id);
    }

    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        return delegate.findByToken(token, tokenType);
    }

    private String getCurrentSessionId() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                HttpSession session = request.getSession(false);
                if (session != null) {
                    return session.getId();
                }
            }
        } catch (Exception e) {
            // RequestContextHolder가 사용 불가능한 경우 무시
        }
        return null;
    }
}
