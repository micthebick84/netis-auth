package com.example.authserver.config;

import com.example.authserver.service.CustomUserDetails;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;

import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class JwtTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    @Override
    public void customize(JwtEncodingContext context) {
        Authentication principal = context.getPrincipal();

        if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
            context.getClaims().claims(claims -> {
                // 커스텀 클레임 추가
                if (principal instanceof UsernamePasswordAuthenticationToken) {
                    Object principalObj = principal.getPrincipal();
                    if (principalObj instanceof CustomUserDetails userDetails) {
                        // 역직렬화된 경우 getUser()가 null일 수 있으므로 직접 필드 사용
                        claims.put("username", userDetails.getUsername());
                        claims.put("email", userDetails.getEmail());
                        // String으로 변환하여 직렬화 이슈 방지
                        claims.put("user_id", String.valueOf(userDetails.getId()));

                        // 사용자 권한 정보 추가
                        Set<String> authorities = userDetails.getAuthorities().stream()
                                .map(GrantedAuthority::getAuthority)
                                .collect(Collectors.toSet());
                        claims.put("authorities", authorities);
                    }
                }

                // 승인된 스코프 추가
                Set<String> scopes = context.getAuthorizedScopes();
                if (scopes != null && !scopes.isEmpty()) {
                    claims.put("scope", String.join(" ", scopes));
                }
            });
        }

        // ID Token 커스터마이징
        if (OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue())) {
            context.getClaims().claims(claims -> {
                if (principal instanceof UsernamePasswordAuthenticationToken) {
                    Object principalObj = principal.getPrincipal();
                    if (principalObj instanceof CustomUserDetails userDetails) {
                        // 역직렬화된 경우 getUser()가 null일 수 있으므로 직접 필드 사용
                        claims.put("name", userDetails.getUsername());
                        claims.put("email", userDetails.getEmail());
                        claims.put("preferred_username", userDetails.getUsername());
                        claims.put("email_verified", true);
                    }

                    // sid (Session ID) 클레임은 의도적으로 제외
                    // Spring Security의 Session Fixation 보호로 인해 세션 ID 불일치 문제가 발생하므로
                    // sid 클레임을 제외하여 OIDC RP-Initiated Logout 시 검증을 건너뛰게 함
                }
            });
        }
    }
}
