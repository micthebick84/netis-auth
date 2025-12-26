package com.example.authserver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class ClientRegistrationRequest {

    @NotBlank(message = "클라이언트 ID는 필수입니다")
    @Size(min = 3, max = 100, message = "클라이언트 ID는 3~100자여야 합니다")
    private String clientId;

    @NotBlank(message = "클라이언트 시크릿은 필수입니다")
    @Size(min = 8, max = 255, message = "클라이언트 시크릿은 최소 8자여야 합니다")
    private String clientSecret;

    @NotBlank(message = "클라이언트 이름은 필수입니다")
    private String clientName;

    @NotBlank(message = "리다이렉트 URI는 필수입니다")
    private String redirectUri;

    private String postLogoutRedirectUri;

    private Set<String> scopes;

    private Set<String> grantTypes;

    private boolean requireProofKey = false;

    private boolean requireConsent = true;

    private Long accessTokenValiditySeconds = 3600L;

    private Long refreshTokenValiditySeconds = 604800L;
}