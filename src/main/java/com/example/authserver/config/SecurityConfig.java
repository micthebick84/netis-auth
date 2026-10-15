package com.example.authserver.config;

import com.example.authserver.service.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final CustomAuthenticationFailureHandler failureHandler;
    private final CustomAuthenticationSuccessHandler successHandler;
    private final CorsConfigurationSource corsConfigurationSource;
    private final OAuth2LoginParameterFilter oAuth2LoginParameterFilter;

    // false면 회원가입 경로를 모두 차단 (공개 배포용, env REGISTRATION_ENABLED). 매처 순서상 permitAll보다 먼저 걸어야 함.
    @Value("${app.registration-enabled:true}")
    private boolean registrationEnabled;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * API 전용 SecurityFilterChain
     * /api/** 경로에 대해 JWT 기반 인증 적용
     */
    @Bean
    @Order(2)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/**")
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> {
                if (!registrationEnabled) {
                    auth.requestMatchers("/api/auth/register").denyAll();
                }
                auth
                    .requestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/logout", "/api/auth/logout/bearer").permitAll()
                    .anyRequest().authenticated();
            })
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .authorizeHttpRequests(auth -> {
                // 신청 직후 바로 인가 흐름으로 넘기는 경로 — 승인형 신청과 맞지 않아 항상 막는다.
                auth.requestMatchers("/oauth/register").denyAll();
                if (!registrationEnabled) {
                    auth.requestMatchers("/register").denyAll();
                }
                auth
                    .requestMatchers("/login", "/register", "/css/**", "/js/**", "/images/**", "/fonts/**", "/error").permitAll()
                    .requestMatchers("/actuator/**").permitAll()
                    .anyRequest().authenticated();
            })
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .successHandler(successHandler)
                .failureHandler(failureHandler)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
            )
            .addFilterBefore(oAuth2LoginParameterFilter, UsernamePasswordAuthenticationFilter.class)
            .headers(headers -> headers
                .frameOptions(frame -> frame.deny())
                .contentTypeOptions(contentType -> {})
                .cacheControl(cache -> {})
            )
            .userDetailsService(userDetailsService);

        return http.build();
    }
}
