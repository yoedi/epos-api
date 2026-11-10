package com.yoedi.epos;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Boots the full context against PostgreSQL: Flyway migrations and Hibernate validation must pass. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class EposApplicationTests {

    @Test
    void contextLoads() {
    }
}
