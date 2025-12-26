package com.example.authserver.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * OAuth2 Authorization 테이블 스키마 초기화
 * Oracle에서 CLOB 타입은 WHERE 절에서 비교할 수 없으므로 VARCHAR2로 재생성
 */
@Component
@Order(1)  // 가장 먼저 실행
@RequiredArgsConstructor
@Slf4j
public class SchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        if (needsRecreation()) {
            log.info("OAuth2 테이블 스키마 재생성 시작...");
            recreateTables();
            log.info("OAuth2 테이블 스키마 재생성 완료");
        } else {
            log.info("OAuth2 테이블 스키마가 이미 올바른 형식입니다");
        }
    }

    private boolean needsRecreation() {
        try {
            // authorization_code_value 컬럼이 CLOB인지 확인
            String sql = """
                SELECT data_type FROM user_tab_columns
                WHERE table_name = 'OAUTH2_AUTHORIZATION'
                AND column_name = 'AUTHORIZATION_CODE_VALUE'
                """;
            String dataType = jdbcTemplate.queryForObject(sql, String.class);
            return "CLOB".equalsIgnoreCase(dataType);
        } catch (Exception e) {
            // 테이블이 없거나 오류 발생 시 재생성 필요
            return true;
        }
    }

    private void recreateTables() {
        // 기존 테이블 삭제
        dropTableIfExists("oauth2_authorization_consent");
        dropTableIfExists("oauth2_authorization");

        // 새 테이블 생성 (VARCHAR2 타입 사용)
        createAuthorizationTable();
        createAuthorizationConsentTable();
    }

    private void dropTableIfExists(String tableName) {
        try {
            jdbcTemplate.execute("DROP TABLE " + tableName + " CASCADE CONSTRAINTS");
            log.info("테이블 삭제됨: {}", tableName);
        } catch (Exception e) {
            // 테이블이 없으면 무시
            log.debug("테이블이 존재하지 않음: {}", tableName);
        }
    }

    private void createAuthorizationTable() {
        String sql = """
            CREATE TABLE oauth2_authorization (
                id VARCHAR2(100) NOT NULL,
                registered_client_id VARCHAR2(100) NOT NULL,
                principal_name VARCHAR2(200) NOT NULL,
                authorization_grant_type VARCHAR2(100) NOT NULL,
                authorized_scopes VARCHAR2(1000),
                attributes CLOB,
                state VARCHAR2(500),
                authorization_code_value VARCHAR2(4000),
                authorization_code_issued_at TIMESTAMP,
                authorization_code_expires_at TIMESTAMP,
                authorization_code_metadata VARCHAR2(2000),
                access_token_value VARCHAR2(4000),
                access_token_issued_at TIMESTAMP,
                access_token_expires_at TIMESTAMP,
                access_token_metadata VARCHAR2(2000),
                access_token_type VARCHAR2(100),
                access_token_scopes VARCHAR2(1000),
                oidc_id_token_value VARCHAR2(4000),
                oidc_id_token_issued_at TIMESTAMP,
                oidc_id_token_expires_at TIMESTAMP,
                oidc_id_token_metadata VARCHAR2(2000),
                refresh_token_value VARCHAR2(4000),
                refresh_token_issued_at TIMESTAMP,
                refresh_token_expires_at TIMESTAMP,
                refresh_token_metadata VARCHAR2(2000),
                user_code_value VARCHAR2(4000),
                user_code_issued_at TIMESTAMP,
                user_code_expires_at TIMESTAMP,
                user_code_metadata VARCHAR2(2000),
                device_code_value VARCHAR2(4000),
                device_code_issued_at TIMESTAMP,
                device_code_expires_at TIMESTAMP,
                device_code_metadata VARCHAR2(2000),
                PRIMARY KEY (id)
            )
            """;
        jdbcTemplate.execute(sql);
        log.info("테이블 생성됨: oauth2_authorization");
    }

    private void createAuthorizationConsentTable() {
        String sql = """
            CREATE TABLE oauth2_authorization_consent (
                registered_client_id VARCHAR2(100) NOT NULL,
                principal_name VARCHAR2(200) NOT NULL,
                authorities VARCHAR2(1000) NOT NULL,
                PRIMARY KEY (registered_client_id, principal_name)
            )
            """;
        jdbcTemplate.execute(sql);
        log.info("테이블 생성됨: oauth2_authorization_consent");
    }
}
