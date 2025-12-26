package com.example.authserver.service;

import com.example.authserver.entity.User;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class JwtTokenService {

    private final JWKSource<SecurityContext> jwkSource;

    private static final long ACCESS_TOKEN_VALIDITY_SECONDS = 3600; // 1시간
    private static final long REFRESH_TOKEN_VALIDITY_SECONDS = 604800; // 7일

    public String generateAccessToken(User user) {
        return generateToken(user, ACCESS_TOKEN_VALIDITY_SECONDS, "access");
    }

    public String generateRefreshToken(User user) {
        return generateToken(user, REFRESH_TOKEN_VALIDITY_SECONDS, "refresh");
    }

    public long getAccessTokenExpiresIn() {
        return ACCESS_TOKEN_VALIDITY_SECONDS;
    }

    private String generateToken(User user, long validitySeconds, String tokenType) {
        try {
            JWKSet jwkSet = new JWKSet(jwkSource.get(new com.nimbusds.jose.jwk.JWKSelector(
                    new com.nimbusds.jose.jwk.JWKMatcher.Builder().build()), null));

            JWK jwk = jwkSet.getKeys().get(0);
            RSAKey rsaKey = jwk.toRSAKey();
            JWSSigner signer = new RSASSASigner(rsaKey);

            Instant now = Instant.now();
            Instant expiry = now.plus(validitySeconds, ChronoUnit.SECONDS);

            List<String> authorities = user.getAuthorities().stream()
                    .map(auth -> auth.getAuthority())
                    .collect(Collectors.toList());

            JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                    .subject(user.getUsername())
                    .issuer("http://localhost:9000")
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(expiry))
                    .jwtID(UUID.randomUUID().toString())
                    .claim("token_type", tokenType)
                    .claim("user_id", user.getId())
                    .claim("email", user.getEmail())
                    .claim("authorities", authorities);

            if ("access".equals(tokenType)) {
                claimsBuilder.claim("scope", List.of("openid", "profile", "email"));
            }

            JWTClaimsSet claimsSet = claimsBuilder.build();

            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .keyID(rsaKey.getKeyID())
                            .build(),
                    claimsSet
            );

            signedJWT.sign(signer);
            return signedJWT.serialize();

        } catch (Exception e) {
            log.error("JWT 토큰 생성 실패", e);
            throw new RuntimeException("JWT 토큰 생성에 실패했습니다", e);
        }
    }
}
