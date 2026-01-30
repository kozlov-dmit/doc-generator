package com.example.envdoc.service;

import com.example.envdoc.model.DefinitionType;
import com.example.envdoc.model.EnvVariable;
import com.example.envdoc.model.UsagePurpose;
import com.example.envdoc.model.VariableDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UsageAnalyzerTest {

    private UsageAnalyzer usageAnalyzer;
    private SourceCodeAnalyzer sourceCodeAnalyzer;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        sourceCodeAnalyzer = new SourceCodeAnalyzer();
        usageAnalyzer = new UsageAnalyzer(sourceCodeAnalyzer);
    }

    @Test
    void shouldDetectDatabasePurpose() {
        // Given
        String className = "com.example.config.DataSourceConfig";
        String methodName = "dataSource";
        String context = "HikariDataSource hikariDataSource = new HikariDataSource()";

        // When
        UsagePurpose purpose = usageAnalyzer.detectPurpose(className, methodName, context);

        // Then
        assertEquals(UsagePurpose.DATABASE_CONNECTION, purpose);
    }

    @Test
    void shouldDetectAuthenticationPurpose() {
        // Given
        String className = "com.example.security.JwtTokenProvider";
        String methodName = "generateToken";
        String context = "JWT token generation with secret key";

        // When
        UsagePurpose purpose = usageAnalyzer.detectPurpose(className, methodName, context);

        // Then
        assertEquals(UsagePurpose.AUTHENTICATION, purpose);
    }

    @Test
    void shouldDetectExternalApiPurpose() {
        // Given
        String className = "com.example.client.PaymentClient";
        String methodName = "callApi";
        String context = "RestTemplate restTemplate calling external endpoint";

        // When
        UsagePurpose purpose = usageAnalyzer.detectPurpose(className, methodName, context);

        // Then
        assertEquals(UsagePurpose.EXTERNAL_API, purpose);
    }

    @Test
    void shouldDetectCachePurpose() {
        // Given
        String className = "com.example.config.RedisConfig";
        String methodName = "redisConnectionFactory";
        String context = "Redis connection factory configuration";

        // When
        UsagePurpose purpose = usageAnalyzer.detectPurpose(className, methodName, context);

        // Then
        assertEquals(UsagePurpose.CACHE_CONFIG, purpose);
    }

    @Test
    void shouldDetectMessagingPurpose() {
        // Given
        String className = "com.example.config.KafkaConfig";
        String methodName = "kafkaTemplate";
        String context = "Kafka producer configuration";

        // When
        UsagePurpose purpose = usageAnalyzer.detectPurpose(className, methodName, context);

        // Then
        assertEquals(UsagePurpose.MESSAGING_CONFIG, purpose);
    }

    @Test
    void shouldAnalyzeUsagesInRepository() throws IOException {
        // Given
        Path javaDir = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(javaDir);

        // Создаём сервис с @Value полем
        String serviceContent = """
            package com.example;

            import org.springframework.beans.factory.annotation.Value;
            import org.springframework.stereotype.Service;

            @Service
            public class MyService {

                @Value("${DATABASE_URL}")
                private String databaseUrl;

                public void connectToDatabase() {
                    // Использует databaseUrl для подключения
                    System.out.println(databaseUrl);
                }

                public void healthCheck() {
                    // Проверка здоровья с использованием databaseUrl
                    if (databaseUrl != null) {
                        System.out.println("Database is configured");
                    }
                }
            }
            """;

        Files.writeString(javaDir.resolve("MyService.java"), serviceContent);

        // Создаём переменную окружения
        Map<String, EnvVariable> variables = new HashMap<>();
        EnvVariable dbUrl = EnvVariable.builder()
                .name("DATABASE_URL")
                .required(true)
                .definition(VariableDefinition.builder()
                        .type(DefinitionType.SPRING_VALUE)
                        .filePath("src/main/java/com/example/MyService.java")
                        .lineNumber(10)
                        .build())
                .usages(new ArrayList<>())
                .build();
        variables.put("DATABASE_URL", dbUrl);

        // When
        usageAnalyzer.analyzeUsages(variables, tempDir);

        // Then
        EnvVariable analyzedVar = variables.get("DATABASE_URL");
        assertNotNull(analyzedVar);
        assertFalse(analyzedVar.getUsages().isEmpty());

        // Должны быть найдены методы, использующие поле databaseUrl
        assertTrue(analyzedVar.getUsages().stream()
                .anyMatch(u -> u.getMethodName().equals("connectToDatabase")));
    }

    @Test
    void shouldDeduplicateUsages() throws IOException {
        // Given
        Path javaDir = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(javaDir);

        String serviceContent = """
            package com.example;

            import org.springframework.beans.factory.annotation.Value;
            import org.springframework.stereotype.Service;

            @Service
            public class DuplicateService {

                @Value("${API_KEY}")
                private String apiKey;

                public void method1() {
                    System.out.println(apiKey);
                }

                public void method2() {
                    System.out.println(apiKey);
                }
            }
            """;

        Files.writeString(javaDir.resolve("DuplicateService.java"), serviceContent);

        Map<String, EnvVariable> variables = new HashMap<>();
        EnvVariable apiKey = EnvVariable.builder()
                .name("API_KEY")
                .required(true)
                .definition(VariableDefinition.builder()
                        .type(DefinitionType.SPRING_VALUE)
                        .build())
                .usages(new ArrayList<>())
                .build();
        variables.put("API_KEY", apiKey);

        // When
        usageAnalyzer.analyzeUsages(variables, tempDir);

        // Then
        EnvVariable analyzedVar = variables.get("API_KEY");

        // Проверяем, что нет дубликатов (каждый метод встречается только раз)
        long method1Count = analyzedVar.getUsages().stream()
                .filter(u -> u.getMethodName().equals("method1"))
                .count();
        long method2Count = analyzedVar.getUsages().stream()
                .filter(u -> u.getMethodName().equals("method2"))
                .count();

        assertEquals(1, method1Count);
        assertEquals(1, method2Count);
    }

    @Test
    void shouldReturnOtherPurposeForUnknownContext() {
        // Given
        String className = "com.example.util.Helper";
        String methodName = "doSomething";
        String context = "Some generic operation";

        // When
        UsagePurpose purpose = usageAnalyzer.detectPurpose(className, methodName, context);

        // Then
        assertEquals(UsagePurpose.OTHER, purpose);
    }
}
