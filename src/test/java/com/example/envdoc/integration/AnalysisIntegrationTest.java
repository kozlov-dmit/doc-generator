package com.example.envdoc.integration;

import com.example.envdoc.model.AnalysisResult;
import com.example.envdoc.model.EnvVariable;
import com.example.envdoc.service.documentation.DocumentGenerator;
import com.example.envdoc.service.extraction.EnvVarExtractor;
import com.example.envdoc.service.extraction.SourceCodeAnalyzer;
import com.example.envdoc.service.extraction.UsageAnalyzer;
import com.example.envdoc.config.OutputConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционный тест полного цикла анализа (без клонирования и GigaChat).
 */
class AnalysisIntegrationTest {

    @TempDir
    Path repoDir;

    @TempDir
    Path outputDir;

    private EnvVarExtractor envVarExtractor;
    private UsageAnalyzer usageAnalyzer;
    private DocumentGenerator documentGenerator;

    @BeforeEach
    void setUp() {
        SourceCodeAnalyzer sourceCodeAnalyzer = new SourceCodeAnalyzer();
        envVarExtractor = new EnvVarExtractor(sourceCodeAnalyzer);
        usageAnalyzer = new UsageAnalyzer(sourceCodeAnalyzer);

        OutputConfig outputConfig = new OutputConfig();
        outputConfig.getMarkdown().setPath(outputDir.toString());
        documentGenerator = new DocumentGenerator(outputConfig);
    }

    @Test
    void shouldAnalyzeSpringBootProject() throws IOException {
        // Given: создаём тестовый Spring Boot проект
        createTestProject();

        // When: извлекаем переменные
        Map<String, EnvVariable> variables = envVarExtractor.extractAllVariables(repoDir);

        // And: анализируем использование
        usageAnalyzer.analyzeUsages(variables, repoDir);

        // Then: проверяем найденные переменные
        assertFalse(variables.isEmpty(), "Should find environment variables");

        // Проверяем DATABASE_URL
        EnvVariable dbUrl = variables.get("DATABASE_URL");
        assertNotNull(dbUrl, "Should find DATABASE_URL");
        assertTrue(dbUrl.isRequired(), "DATABASE_URL should be required");

        // Проверяем API_KEY
        EnvVariable apiKey = variables.get("API_KEY");
        assertNotNull(apiKey, "Should find API_KEY");

        // Проверяем CACHE_TTL с default value
        EnvVariable cacheTtl = variables.get("CACHE_TTL");
        assertNotNull(cacheTtl, "Should find CACHE_TTL");
        assertFalse(cacheTtl.isRequired(), "CACHE_TTL should not be required (has default)");
        assertEquals("3600", cacheTtl.getDefaultValue());

        // When: генерируем документацию
        AnalysisResult result = AnalysisResult.builder()
                .projectName("test-project")
                .repositoryUrl("https://github.com/test/repo")
                .branch("main")
                .startedAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .variables(new ArrayList<>(variables.values()))
                .build();

        Path outputPath = documentGenerator.generateAndSave(result);

        // Then: проверяем файл документации
        assertTrue(Files.exists(outputPath), "Documentation file should exist");

        String content = Files.readString(outputPath);
        assertTrue(content.contains("DATABASE_URL"), "Doc should contain DATABASE_URL");
        assertTrue(content.contains("API_KEY"), "Doc should contain API_KEY");
        assertTrue(content.contains("## Примеры конфигурации"), "Doc should have config examples");
    }

    private void createTestProject() throws IOException {
        // Создаём структуру проекта
        Path resourcesDir = repoDir.resolve("src/main/resources");
        Path javaDir = repoDir.resolve("src/main/java/com/example");
        Files.createDirectories(resourcesDir);
        Files.createDirectories(javaDir);

        // application.yml
        String yamlContent = """
            spring:
              application:
                name: test-service
              datasource:
                url: ${DATABASE_URL}
                username: ${DB_USER:admin}
                password: ${DB_PASSWORD}
              redis:
                host: ${REDIS_HOST:localhost}

            app:
              api-key: ${API_KEY}
              cache-ttl: ${CACHE_TTL:3600}
              feature-enabled: ${FEATURE_X_ENABLED:false}
            """;
        Files.writeString(resourcesDir.resolve("application.yml"), yamlContent);

        // DataSourceConfig.java
        String dataSourceConfig = """
            package com.example;

            import org.springframework.beans.factory.annotation.Value;
            import org.springframework.context.annotation.Bean;
            import org.springframework.context.annotation.Configuration;

            @Configuration
            public class DataSourceConfig {

                @Value("${DATABASE_URL}")
                private String databaseUrl;

                @Value("${DB_USER:admin}")
                private String dbUser;

                @Bean
                public Object dataSource() {
                    // Использование databaseUrl и dbUser для создания DataSource
                    System.out.println("Connecting to: " + databaseUrl);
                    System.out.println("User: " + dbUser);
                    return new Object();
                }
            }
            """;
        Files.writeString(javaDir.resolve("DataSourceConfig.java"), dataSourceConfig);

        // ApiClient.java
        String apiClient = """
            package com.example;

            import org.springframework.beans.factory.annotation.Value;
            import org.springframework.stereotype.Service;

            @Service
            public class ApiClient {

                @Value("${API_KEY}")
                private String apiKey;

                public void callExternalApi() {
                    // Использование apiKey для вызова внешнего API
                    System.out.println("Calling API with key: " + apiKey);
                }
            }
            """;
        Files.writeString(javaDir.resolve("ApiClient.java"), apiClient);

        // CacheConfig.java
        String cacheConfig = """
            package com.example;

            import org.springframework.beans.factory.annotation.Value;
            import org.springframework.context.annotation.Configuration;

            @Configuration
            public class CacheConfig {

                @Value("${CACHE_TTL:3600}")
                private int cacheTtl;

                @Value("${REDIS_HOST:localhost}")
                private String redisHost;

                public void configureCache() {
                    System.out.println("Cache TTL: " + cacheTtl);
                    System.out.println("Redis host: " + redisHost);
                }
            }
            """;
        Files.writeString(javaDir.resolve("CacheConfig.java"), cacheConfig);

        // FeatureService.java с System.getenv
        String featureService = """
            package com.example;

            public class FeatureService {

                public boolean isDebugMode() {
                    String debug = System.getenv("DEBUG_MODE");
                    return "true".equalsIgnoreCase(debug);
                }

                public String getSecretKey() {
                    return System.getenv("SECRET_KEY");
                }
            }
            """;
        Files.writeString(javaDir.resolve("FeatureService.java"), featureService);
    }

    @Test
    void shouldHandleEmptyProject() throws IOException {
        // Given: пустой проект
        Path javaDir = repoDir.resolve("src/main/java");
        Files.createDirectories(javaDir);

        // When
        Map<String, EnvVariable> variables = envVarExtractor.extractAllVariables(repoDir);

        // Then
        assertTrue(variables.isEmpty(), "Empty project should have no variables");
    }

    @Test
    void shouldHandleProjectWithOnlyProperties() throws IOException {
        // Given: проект только с properties файлом
        Path resourcesDir = repoDir.resolve("src/main/resources");
        Files.createDirectories(resourcesDir);

        String propsContent = """
            server.port=${SERVER_PORT:8080}
            app.name=${APP_NAME}
            """;
        Files.writeString(resourcesDir.resolve("application.properties"), propsContent);

        // When
        Map<String, EnvVariable> variables = envVarExtractor.extractAllVariables(repoDir);

        // Then
        assertEquals(2, variables.size());
        assertNotNull(variables.get("SERVER_PORT"));
        assertNotNull(variables.get("APP_NAME"));
    }
}
