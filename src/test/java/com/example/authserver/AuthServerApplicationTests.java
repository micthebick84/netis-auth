package com.example.authserver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** test 프로필(H2 인메모리) — 없으면 application.yml의 실제 PostgreSQL에 접속해 실패한다. */
@SpringBootTest
@ActiveProfiles("test")
class AuthServerApplicationTests {

    @Test
    void contextLoads() {
    }
}
