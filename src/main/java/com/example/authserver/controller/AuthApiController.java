package com.example.authserver.controller;

import com.example.authserver.dto.ApiResponse;
import com.example.authserver.dto.LoginRequest;
import com.example.authserver.dto.LoginResponse;
import com.example.authserver.dto.LogoutRequest;
import com.example.authserver.dto.RegisterRequest;
import com.example.authserver.entity.User;
import com.example.authserver.service.AuthService;
import com.example.authserver.service.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthApiController {

    private final AuthService authService;
    private final OAuth2AuthorizationService authorizationService;

    /**
     * 사용자 로그인 API
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        try {
            LoginResponse response = authService.login(request);
            return ResponseEntity.ok(ApiResponse.success("로그인 성공", response));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (LockedException e) {
            return ResponseEntity.status(HttpStatus.LOCKED)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("로그인 처리 중 오류 발생", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("로그인 처리 중 오류가 발생했습니다"));
        }
    }

    /**
     * 계정 신청 API (비활성 계정 생성, 관리자 승인 후 로그인)
     * POST /api/auth/register
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Map<String, Object>>> register(@Valid @RequestBody RegisterRequest request) {
        try {
            User user = authService.register(request);

            Map<String, Object> userData = new HashMap<>();
            userData.put("id", user.getId());
            userData.put("username", user.getUsername());
            userData.put("email", user.getEmail());

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("계정 신청이 접수되었습니다. 관리자 승인 후 로그인할 수 있습니다", userData));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("회원가입 처리 중 오류 발생", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("회원가입 처리 중 오류가 발생했습니다"));
        }
    }

    /**
     * 현재 로그인한 사용자 정보 조회 API
     * GET /api/auth/me
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCurrentUser(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("인증이 필요합니다"));
        }

        Map<String, Object> userData = new HashMap<>();
        userData.put("id", userDetails.getId());
        userData.put("username", userDetails.getUsername());
        userData.put("email", userDetails.getEmail());
        userData.put("authorities", userDetails.getAuthorities());

        return ResponseEntity.ok(ApiResponse.success(userData));
    }

    /**
     * 토큰 갱신 API
     * POST /api/auth/refresh
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<String>> refreshToken(@RequestHeader("Authorization") String authHeader) {
        // TODO: Refresh Token 검증 및 새 Access Token 발급 구현
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(ApiResponse.error("토큰 갱신 기능은 아직 구현되지 않았습니다"));
    }

    /**
     * 로그아웃 API (Token Revocation)
     * POST /api/auth/logout
     *
     * 클라이언트에서 저장된 토큰을 무효화합니다.
     * Access Token 또는 Refresh Token을 전달하면 해당 Authorization의 모든 토큰이 무효화됩니다.
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Map<String, Object>>> logout(@RequestBody LogoutRequest request) {
        try {
            if (request.getToken() == null || request.getToken().isBlank()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("토큰이 필요합니다"));
            }

            String token = request.getToken();
            String tokenTypeHint = request.getTokenTypeHint();
            OAuth2Authorization authorization = null;

            // 토큰 타입 힌트에 따라 검색
            if ("refresh_token".equals(tokenTypeHint)) {
                authorization = authorizationService.findByToken(token, OAuth2TokenType.REFRESH_TOKEN);
            } else if ("access_token".equals(tokenTypeHint)) {
                authorization = authorizationService.findByToken(token, OAuth2TokenType.ACCESS_TOKEN);
            }

            // 힌트로 못 찾으면 양쪽 모두 검색
            if (authorization == null) {
                authorization = authorizationService.findByToken(token, OAuth2TokenType.ACCESS_TOKEN);
            }
            if (authorization == null) {
                authorization = authorizationService.findByToken(token, OAuth2TokenType.REFRESH_TOKEN);
            }

            if (authorization == null) {
                // 이미 무효화되었거나 존재하지 않는 토큰
                // RFC 7009에 따라 성공으로 응답 (클라이언트 입장에서는 토큰이 무효화된 것과 동일)
                Map<String, Object> result = new HashMap<>();
                result.put("revoked", true);
                result.put("message", "토큰이 이미 무효화되었거나 존재하지 않습니다");
                return ResponseEntity.ok(ApiResponse.success("로그아웃 완료", result));
            }

            // Authorization의 모든 토큰을 무효화
            OAuth2Authorization.Builder authorizationBuilder = OAuth2Authorization.from(authorization);

            // Access Token 무효화
            OAuth2Authorization.Token<OAuth2AccessToken> accessToken = authorization.getAccessToken();
            if (accessToken != null) {
                authorizationBuilder.token(accessToken.getToken(), metadata -> {
                    metadata.put(OAuth2Authorization.Token.INVALIDATED_METADATA_NAME, true);
                });
            }

            // Refresh Token 무효화
            OAuth2Authorization.Token<OAuth2RefreshToken> refreshToken = authorization.getRefreshToken();
            if (refreshToken != null) {
                authorizationBuilder.token(refreshToken.getToken(), metadata -> {
                    metadata.put(OAuth2Authorization.Token.INVALIDATED_METADATA_NAME, true);
                });
            }

            authorizationService.save(authorizationBuilder.build());

            log.info("사용자 로그아웃 처리 완료: Authorization ID={}", authorization.getId());

            Map<String, Object> result = new HashMap<>();
            result.put("revoked", true);
            result.put("message", "모든 토큰이 무효화되었습니다");
            return ResponseEntity.ok(ApiResponse.success("로그아웃 완료", result));

        } catch (Exception e) {
            log.error("로그아웃 처리 중 오류 발생", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("로그아웃 처리 중 오류가 발생했습니다"));
        }
    }

    /**
     * Authorization Header에서 Bearer 토큰으로 로그아웃
     * POST /api/auth/logout/bearer
     *
     * Authorization 헤더에서 토큰을 추출하여 로그아웃 처리합니다.
     */
    @PostMapping("/logout/bearer")
    public ResponseEntity<ApiResponse<Map<String, Object>>> logoutWithBearer(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Authorization 헤더에 Bearer 토큰이 필요합니다"));
            }

            String token = authHeader.substring(7);
            LogoutRequest logoutRequest = LogoutRequest.builder()
                    .token(token)
                    .tokenTypeHint("access_token")
                    .build();

            return logout(logoutRequest);

        } catch (Exception e) {
            log.error("로그아웃 처리 중 오류 발생", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("로그아웃 처리 중 오류가 발생했습니다"));
        }
    }
}
