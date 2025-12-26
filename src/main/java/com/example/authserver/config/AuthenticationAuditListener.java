package com.example.authserver.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Slf4j
public class AuthenticationAuditListener {

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        log.info("LOGIN_SUCCESS: user={}, time={}",
                event.getAuthentication().getName(),
                LocalDateTime.now());
    }

    @EventListener
    public void onAuthenticationFailure(AuthenticationFailureBadCredentialsEvent event) {
        log.warn("LOGIN_FAILURE: user={}, time={}, reason={}",
                event.getAuthentication().getName(),
                LocalDateTime.now(),
                event.getException().getMessage());
    }

    @EventListener
    public void onLogoutSuccess(LogoutSuccessEvent event) {
        log.info("LOGOUT_SUCCESS: user={}, time={}",
                event.getAuthentication().getName(),
                LocalDateTime.now());
    }
}
