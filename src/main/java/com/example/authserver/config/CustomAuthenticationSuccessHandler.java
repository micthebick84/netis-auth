package com.example.authserver.config;

import com.example.authserver.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CustomAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final UserRepository userRepository;

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

        // OAuth2 로그인 옵션 체크
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

            response.sendRedirect(authorizeUrl);
            return;
        }

        super.setDefaultTargetUrl("/");
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
