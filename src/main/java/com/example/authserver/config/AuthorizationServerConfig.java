package com.example.authserver.config;

import com.example.authserver.config.oauth2.PasswordGrantAuthenticationConverter;
import com.example.authserver.config.oauth2.PasswordGrantAuthenticationProvider;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.token.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcLogoutAuthenticationToken;
import org.springframework.web.cors.CorsConfigurationSource;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.UUID;

@Configuration
public class AuthorizationServerConfig {

    private final OidcUserInfoMapper oidcUserInfoMapper;
    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final CorsConfigurationSource corsConfigurationSource;

    public AuthorizationServerConfig(OidcUserInfoMapper oidcUserInfoMapper,
                                     UserDetailsService userDetailsService,
                                     PasswordEncoder passwordEncoder,
                                     CorsConfigurationSource corsConfigurationSource) {
        this.oidcUserInfoMapper = oidcUserInfoMapper;
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(
            HttpSecurity http,
            OAuth2AuthorizationService authorizationService,
            OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator,
            RegisteredClientRepository registeredClientRepository,
            JwtDecoder jwtDecoder) throws Exception {

        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);

        http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
                .tokenEndpoint(tokenEndpoint -> tokenEndpoint
                        // Password Grant Converter 및 Provider 등록
                        .accessTokenRequestConverter(new PasswordGrantAuthenticationConverter())
                        .authenticationProvider(new PasswordGrantAuthenticationProvider(
                                authorizationService,
                                tokenGenerator,
                                userDetailsService,
                                passwordEncoder
                        ))
                )
                .oidc(oidc -> oidc
                        .userInfoEndpoint(userInfo -> userInfo
                                .userInfoMapper(oidcUserInfoMapper)
                        )
                        // OIDC RP-Initiated Logout 활성화
                        // CustomOidcLogoutAuthenticationProvider를 사용하여 sid 검증을 건너뜀
                        .logoutEndpoint(logout -> logout
                                .authenticationProvider(new CustomOidcLogoutAuthenticationProvider(
                                        authorizationService,
                                        registeredClientRepository,
                                        jwtDecoder
                                ))
                                .logoutResponseHandler((request, response, authentication) -> {
                                    // 세션 무효화
                                    var session = request.getSession(false);
                                    if (session != null) {
                                        session.invalidate();
                                    }
                                    // SecurityContext 클리어
                                    org.springframework.security.core.context.SecurityContextHolder.clearContext();

                                    String redirectUri = null;
                                    String state = null;

                                    // 1. OidcLogoutAuthenticationToken에서 추출 시도
                                    if (authentication instanceof OidcLogoutAuthenticationToken logoutToken) {
                                        redirectUri = logoutToken.getPostLogoutRedirectUri();
                                        state = logoutToken.getState();
                                        System.out.println("[OIDC Logout] Token postLogoutRedirectUri: " + redirectUri);
                                    }

                                    // 2. 요청 파라미터에서 직접 추출 (fallback)
                                    if (redirectUri == null || redirectUri.isBlank()) {
                                        redirectUri = request.getParameter("post_logout_redirect_uri");
                                        System.out.println("[OIDC Logout] Request param postLogoutRedirectUri: " + redirectUri);
                                    }
                                    if (state == null || state.isBlank()) {
                                        state = request.getParameter("state");
                                    }

                                    // 리다이렉트 URI가 있으면 해당 URI로 리다이렉트
                                    if (redirectUri != null && !redirectUri.isBlank()) {
                                        if (state != null && !state.isBlank()) {
                                            redirectUri += (redirectUri.contains("?") ? "&" : "?") + "state=" + state;
                                        }
                                        System.out.println("[OIDC Logout] Redirecting to: " + redirectUri);
                                        response.sendRedirect(redirectUri);
                                        return;
                                    }

                                    // 기본 리다이렉트
                                    System.out.println("[OIDC Logout] No redirect URI, going to /login?logout");
                                    response.sendRedirect("/login?logout");
                                })
                        )
                );

        http
                // OAuth2 엔드포인트에 CORS 적용
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/login"),
                                new MediaTypeRequestMatcher(MediaType.TEXT_HTML)
                        )
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
                .issuer("http://localhost:9000")
                .authorizationEndpoint("/oauth2/authorize")
                .tokenEndpoint("/oauth2/token")
                .tokenIntrospectionEndpoint("/oauth2/introspect")
                .tokenRevocationEndpoint("/oauth2/revoke")
                .jwkSetEndpoint("/oauth2/jwks")
                .oidcUserInfoEndpoint("/userinfo")
                .oidcClientRegistrationEndpoint("/connect/register")
                .oidcLogoutEndpoint("/connect/logout")
                .build();
    }

    @Bean
    public TokenSettings tokenSettings() {
        return TokenSettings.builder()
                .accessTokenTimeToLive(Duration.ofHours(1))
                .refreshTokenTimeToLive(Duration.ofDays(7))
                .reuseRefreshTokens(false)
                .idTokenSignatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
    }

    private static final String KEY_DIRECTORY = ".keys";
    private static final String PUBLIC_KEY_FILE = KEY_DIRECTORY + "/public.key";
    private static final String PRIVATE_KEY_FILE = KEY_DIRECTORY + "/private.key";
    private static final String KEY_ID_FILE = KEY_DIRECTORY + "/key.id";

    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        KeyPair keyPair = loadOrGenerateRsaKey();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

        String keyId = loadOrGenerateKeyId();

        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(keyId)
                .build();

        JWKSet jwkSet = new JWKSet(rsaKey);
        return new ImmutableJWKSet<>(jwkSet);
    }

    private KeyPair loadOrGenerateRsaKey() {
        File publicKeyFile = new File(PUBLIC_KEY_FILE);
        File privateKeyFile = new File(PRIVATE_KEY_FILE);

        if (publicKeyFile.exists() && privateKeyFile.exists()) {
            try {
                return loadKeyPair(publicKeyFile, privateKeyFile);
            } catch (Exception e) {
                System.out.println("기존 키 로드 실패, 새 키 생성: " + e.getMessage());
            }
        }

        return generateAndSaveRsaKey();
    }

    private KeyPair loadKeyPair(File publicKeyFile, File privateKeyFile) throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");

        byte[] publicKeyBytes = readFile(publicKeyFile);
        X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);
        RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(publicKeySpec);

        byte[] privateKeyBytes = readFile(privateKeyFile);
        PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
        RSAPrivateKey privateKey = (RSAPrivateKey) keyFactory.generatePrivate(privateKeySpec);

        System.out.println("RSA 키를 파일에서 로드했습니다.");
        return new KeyPair(publicKey, privateKey);
    }

    private KeyPair generateAndSaveRsaKey() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();

            // 키 디렉토리 생성
            new File(KEY_DIRECTORY).mkdirs();

            // 키 파일 저장
            writeFile(new File(PUBLIC_KEY_FILE), keyPair.getPublic().getEncoded());
            writeFile(new File(PRIVATE_KEY_FILE), keyPair.getPrivate().getEncoded());

            System.out.println("새 RSA 키를 생성하고 파일에 저장했습니다.");
            return keyPair;
        } catch (Exception ex) {
            throw new IllegalStateException("RSA 키 생성 실패", ex);
        }
    }

    private String loadOrGenerateKeyId() {
        File keyIdFile = new File(KEY_ID_FILE);
        if (keyIdFile.exists()) {
            try {
                return new String(readFile(keyIdFile)).trim();
            } catch (Exception e) {
                // 무시하고 새로 생성
            }
        }

        String keyId = UUID.randomUUID().toString();
        try {
            new File(KEY_DIRECTORY).mkdirs();
            writeFile(keyIdFile, keyId.getBytes());
        } catch (Exception e) {
            // 저장 실패해도 계속 진행
        }
        return keyId;
    }

    private byte[] readFile(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            return fis.readAllBytes();
        }
    }

    private void writeFile(File file, byte[] data) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(data);
        }
    }

    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    @Bean
    public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    public OAuth2TokenGenerator<?> tokenGenerator(JwtEncoder jwtEncoder,
                                                   OAuth2TokenCustomizer<JwtEncodingContext> jwtCustomizer) {
        JwtGenerator jwtGenerator = new JwtGenerator(jwtEncoder);
        jwtGenerator.setJwtCustomizer(jwtCustomizer);
        OAuth2AccessTokenGenerator accessTokenGenerator = new OAuth2AccessTokenGenerator();
        OAuth2RefreshTokenGenerator refreshTokenGenerator = new OAuth2RefreshTokenGenerator();
        return new DelegatingOAuth2TokenGenerator(
                jwtGenerator, accessTokenGenerator, refreshTokenGenerator);
    }
}
