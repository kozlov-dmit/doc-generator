package com.example.envdoc;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class EnvDocAgentApplicationTests {

    @Test
    void contextLoads() {
        // Проверяет, что контекст Spring загружается корректно
    }
}
