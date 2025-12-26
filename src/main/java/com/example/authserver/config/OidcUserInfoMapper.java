package com.example.authserver.config;

import com.example.authserver.entity.User;
import com.example.authserver.service.CustomUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcUserInfoAuthenticationContext;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Set;
import java.util.function.Function;

@Component
public class OidcUserInfoMapper implements Function<OidcUserInfoAuthenticationContext, OidcUserInfo> {

    @Override
    public OidcUserInfo apply(OidcUserInfoAuthenticationContext context) {
        OidcUserInfo.Builder userInfoBuilder = OidcUserInfo.builder();

        OAuth2Authorization authorization = context.getAuthorization();
        Authentication principal = authorization.getAttribute(Principal.class.getName());

        if (principal != null && principal.getPrincipal() instanceof CustomUserDetails userDetails) {
            User user = userDetails.getUser();

            // Subject는 항상 포함
            userInfoBuilder.subject(user.getUsername());

            Set<String> scopes = authorization.getAuthorizedScopes();

            // Profile scope
            if (scopes.contains(OidcScopes.PROFILE)) {
                userInfoBuilder.name(user.getUsername());
                userInfoBuilder.preferredUsername(user.getUsername());
            }

            // Email scope
            if (scopes.contains(OidcScopes.EMAIL)) {
                userInfoBuilder.email(user.getEmail());
                userInfoBuilder.emailVerified(true);
            }
        }

        return userInfoBuilder.build();
    }
}
