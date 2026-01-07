package com.example.authserver.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogoutRequest {

    /**
     * Access Token 또는 Refresh Token
     */
    private String token;

    /**
     * 토큰 타입 힌트 (선택사항)
     * - "access_token"
     * - "refresh_token"
     */
    private String tokenTypeHint;
}
