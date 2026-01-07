package com.example.authserver.config;

import com.example.authserver.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CustomAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final RequestCache requestCache = new HttpSessionRequestCache();

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        String username = authentication.getName();

        userRepository.findByUsername(username).ifPresent(user -> {
            user.setFailedAttempt(0);
            user.setLockTime(null);
            userRepository.save(user);
        });

        // 1. 먼저 저장된 요청 확인 (OAuth2 authorization 요청 등)
        SavedRequest savedRequest = requestCache.getRequest(request, response);
        if (savedRequest != null) {
            String targetUrl = savedRequest.getRedirectUrl();
            // OAuth2 authorization 요청이면 저장된 요청으로 리다이렉트
            if (targetUrl != null && targetUrl.contains("/oauth2/authorize")) {
                logger.debug("Redirecting to saved OAuth2 authorization request: " + targetUrl);
                super.onAuthenticationSuccess(request, response, authentication);
                return;
            }
        }

        // 2. 폼에서 OAuth2 로그인 옵션 체크
        HttpSession session = request.getSession(false);
        String oauth2Login = null;
        String clientId = null;
        String redirectUri = null;
        String scope = null;

        if (session != null) {
            oauth2Login = (String) session.getAttribute("oauth2_login");
            clientId = (String) session.getAttribute("client_id");
            redirectUri = (String) session.getAttribute("redirect_uri");
            scope = (String) session.getAttribute("scope");

            // 세션에서 OAuth2 파라미터 제거
            session.removeAttribute("oauth2_login");
            session.removeAttribute("client_id");
            session.removeAttribute("redirect_uri");
            session.removeAttribute("scope");
        }

        if ("true".equals(oauth2Login) && clientId != null && redirectUri != null) {
            // OAuth2 Authorization Code 발급을 위한 리다이렉트
            String authorizeUrl = UriComponentsBuilder.fromPath("/oauth2/authorize")
                    .queryParam("response_type", "code")
                    .queryParam("client_id", clientId)
                    .queryParam("redirect_uri", redirectUri)
                    .queryParam("scope", scope != null ? scope : "openid profile email")
                    .queryParam("state", UUID.randomUUID().toString().substring(0, 8))
                    .build()
                    .toUriString();

            logger.debug("Redirecting to OAuth2 authorization: " + authorizeUrl);
            response.sendRedirect(authorizeUrl);
            return;
        }

        // 3. 저장된 요청이 있으면 그것으로 리다이렉트, 없으면 기본 URL로
        super.setDefaultTargetUrl("/");
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
