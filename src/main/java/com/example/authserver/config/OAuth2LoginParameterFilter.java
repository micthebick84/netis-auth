package com.example.authserver.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 로그인 폼에서 OAuth2 파라미터를 세션에 저장하는 필터
 */
@Component
public class OAuth2LoginParameterFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // POST /login 요청일 때만 처리
        if ("POST".equalsIgnoreCase(request.getMethod()) && "/login".equals(request.getServletPath())) {
            String oauth2Login = request.getParameter("oauth2_login");

            if ("true".equals(oauth2Login)) {
                HttpSession session = request.getSession(true);
                session.setAttribute("oauth2_login", "true");
                session.setAttribute("client_id", request.getParameter("client_id"));
                session.setAttribute("redirect_uri", request.getParameter("redirect_uri"));
                session.setAttribute("scope", request.getParameter("scope"));
            }
        }

        filterChain.doFilter(request, response);
    }
}
