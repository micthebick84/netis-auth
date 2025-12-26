package com.example.authserver.config.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * Password Grant 요청을 PasswordGrantAuthenticationToken으로 변환
 */
public class PasswordGrantAuthenticationConverter implements AuthenticationConverter {

    private static final String PASSWORD_GRANT_TYPE = "password";

    @Nullable
    @Override
    public Authentication convert(HttpServletRequest request) {
        // grant_type 확인
        String grantType = request.getParameter(OAuth2ParameterNames.GRANT_TYPE);
        if (!PASSWORD_GRANT_TYPE.equals(grantType)) {
            return null;
        }

        // 요청 파라미터 추출
        MultiValueMap<String, String> parameters = getParameters(request);

        // username 파라미터 확인
        String username = parameters.getFirst(OAuth2ParameterNames.USERNAME);
        if (!StringUtils.hasText(username) ||
            parameters.get(OAuth2ParameterNames.USERNAME).size() != 1) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST);
        }

        // password 파라미터 확인
        String password = parameters.getFirst(OAuth2ParameterNames.PASSWORD);
        if (!StringUtils.hasText(password) ||
            parameters.get(OAuth2ParameterNames.PASSWORD).size() != 1) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST);
        }

        // scope 파라미터 추출
        String scope = parameters.getFirst(OAuth2ParameterNames.SCOPE);
        Set<String> requestedScopes = null;
        if (StringUtils.hasText(scope)) {
            requestedScopes = new HashSet<>(Arrays.asList(StringUtils.delimitedListToStringArray(scope, " ")));
        }

        // 클라이언트 인증 정보
        Authentication clientPrincipal = SecurityContextHolder.getContext().getAuthentication();

        // 추가 파라미터 수집
        Map<String, Object> additionalParameters = new HashMap<>();
        parameters.forEach((key, value) -> {
            if (!key.equals(OAuth2ParameterNames.GRANT_TYPE) &&
                !key.equals(OAuth2ParameterNames.USERNAME) &&
                !key.equals(OAuth2ParameterNames.PASSWORD) &&
                !key.equals(OAuth2ParameterNames.SCOPE)) {
                additionalParameters.put(key, value.get(0));
            }
        });

        return new PasswordGrantAuthenticationToken(
                clientPrincipal,
                username,
                password,
                requestedScopes,
                additionalParameters
        );
    }

    private static MultiValueMap<String, String> getParameters(HttpServletRequest request) {
        Map<String, String[]> parameterMap = request.getParameterMap();
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>(parameterMap.size());
        parameterMap.forEach((key, values) -> {
            for (String value : values) {
                parameters.add(key, value);
            }
        });
        return parameters;
    }
}
