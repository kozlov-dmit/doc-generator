package com.example.envdoc.service.extraction;

import com.example.envdoc.model.DefinitionType;
import com.example.envdoc.model.EnvVariable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EnvVarExtractorTest {

    private EnvVarExtractor extractor;
    private SourceCodeAnalyzer sourceCodeAnalyzer;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        sourceCodeAnalyzer = new SourceCodeAnalyzer();
        extractor = new EnvVarExtractor(sourceCodeAnalyzer);
    }

    @Test
    void shouldExtractVariablesFromYaml() throws IOException {
        // Given
        Path resourcesDir = tempDir.resolve("src/main/resources");
        Files.createDirectories(resourcesDir);

        String yamlContent = """
            spring:
              datasource:
                url: ${DATABASE_URL}
                username: ${DB_USERNAME:admin}
                password: ${DB_PASSWORD}
              redis:
                host: ${REDIS_HOST:localhost}
                port: ${REDIS_PORT:6379}
            """;

        Files.writeString(resourcesDir.resolve("application.yml"), yamlContent);

        // When
        Map<String, EnvVariable> variables = extractor.extractAllVariables(tempDir);

        // Then
        assertEquals(5, variables.size());

        // DATABASE_URL - обязательная (нет default)
        EnvVariable dbUrl = variables.get("DATABASE_URL");
        assertNotNull(dbUrl);
        assertTrue(dbUrl.isRequired());
        assertNull(dbUrl.getDefaultValue());
        assertEquals(DefinitionType.APPLICATION_YAML, dbUrl.getDefinition().getType());

        // DB_USERNAME - опциональная (есть default)
        EnvVariable dbUsername = variables.get("DB_USERNAME");
        assertNotNull(dbUsername);
        assertFalse(dbUsername.isRequired());
        assertEquals("admin", dbUsername.getDefaultValue());

        // REDIS_PORT - опциональная с числовым default
        EnvVariable redisPort = variables.get("REDIS_PORT");
        assertNotNull(redisPort);
        assertFalse(redisPort.isRequired());
        assertEquals("6379", redisPort.getDefaultValue());
    }

    @Test
    void shouldExtractVariablesFromValueAnnotation() throws IOException {
        // Given
        Path javaDir = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(javaDir);

        String javaContent = """
            package com.example;

            import org.springframework.beans.factory.annotation.Value;
            import org.springframework.stereotype.Service;

            @Service
            public class MyService {

                @Value("${API_KEY}")
                private String apiKey;

                @Value("${API_TIMEOUT:30}")
                private int timeout;

                @Value("${FEATURE_ENABLED:false}")
                private boolean featureEnabled;

                public void doSomething() {
                    // использование apiKey
                }
            }
            """;

        Files.writeString(javaDir.resolve("MyService.java"), javaContent);

        // When
        Map<String, EnvVariable> variables = extractor.extractAllVariables(tempDir);

        // Then
        assertEquals(3, variables.size());

        EnvVariable apiKey = variables.get("API_KEY");
        assertNotNull(apiKey);
        assertTrue(apiKey.isRequired());
        assertEquals(DefinitionType.SPRING_VALUE, apiKey.getDefinition().getType());

        EnvVariable timeout = variables.get("API_TIMEOUT");
        assertNotNull(timeout);
        assertFalse(timeout.isRequired());
        assertEquals("30", timeout.getDefaultValue());
    }

    @Test
    void shouldExtractVariablesFromSystemGetenv() throws IOException {
        // Given
        Path javaDir = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(javaDir);

        String javaContent = """
            package com.example;

            public class Config {

                public String getSecret() {
                    return System.getenv("SECRET_KEY");
                }

                public String getOptionalConfig() {
                    String value = System.getenv("OPTIONAL_CONFIG");
                    return value != null ? value : "default";
                }
            }
            """;

        Files.writeString(javaDir.resolve("Config.java"), javaContent);

        // When
        Map<String, EnvVariable> variables = extractor.extractAllVariables(tempDir);

        // Then
        assertEquals(2, variables.size());

        EnvVariable secretKey = variables.get("SECRET_KEY");
        assertNotNull(secretKey);
        assertTrue(secretKey.isRequired());
        assertEquals(DefinitionType.SYSTEM_GETENV, secretKey.getDefinition().getType());
    }

    @Test
    void shouldExtractVariablesFromSystemGetProperty() throws IOException {
        // Given
        Path javaDir = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(javaDir);

        String javaContent = """
            package com.example;

            public class Config {

                public String getConfigPath() {
                    return System.getProperty("config.path");
                }

                public String getLogLevel() {
                    return System.getProperty("log.level", "INFO");
                }
            }
            """;

        Files.writeString(javaDir.resolve("Config.java"), javaContent);

        // When
        Map<String, EnvVariable> variables = extractor.extractAllVariables(tempDir);

        // Then
        assertEquals(2, variables.size());

        EnvVariable configPath = variables.get("CONFIG_PATH");
        assertNotNull(configPath);
        assertTrue(configPath.isRequired());
        assertEquals(DefinitionType.SYSTEM_PROPERTY, configPath.getDefinition().getType());

        EnvVariable logLevel = variables.get("LOG_LEVEL");
        assertNotNull(logLevel);
        assertFalse(logLevel.isRequired());
        assertEquals("INFO", logLevel.getDefaultValue());
    }

    @Test
    void shouldExtractDefaultValueFromEnvironmentGetProperty() throws IOException {
        // Given
        Path javaDir = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(javaDir);

        String javaContent = """
            package com.example;

            import org.springframework.core.env.Environment;

            public class EnvConfig {
                private final Environment environment;

                public EnvConfig(Environment environment) {
                    this.environment = environment;
                }

                public String getUrl() {
                    return environment.getProperty("service.url", "http://localhost");
                }
            }
            """;

        Files.writeString(javaDir.resolve("EnvConfig.java"), javaContent);

        // When
        Map<String, EnvVariable> variables = extractor.extractAllVariables(tempDir);

        // Then
        EnvVariable url = variables.get("SERVICE_URL");
        assertNotNull(url);
        assertEquals("http://localhost", url.getDefaultValue());
        assertFalse(url.isRequired());
    }

    @Test
    void shouldExtractDefaultValueFromConfigProperties() throws IOException {
        // Given
        Path javaDir = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(javaDir);

        Path resourcesDir = tempDir.resolve("src/main/resources");
        Files.createDirectories(resourcesDir);

        String javaContent = """
            package com.example;

            import org.springframework.boot.context.properties.ConfigurationProperties;
            import org.springframework.stereotype.Component;

            @Component
            @ConfigurationProperties(prefix = "app.db")
            public class DbConfig {
                private String url = "jdbc:postgresql://localhost:5432/db";
                private int poolSize = 10;

                public String getUrl() { return url; }
                public void setUrl(String url) { this.url = url; }
                public int getPoolSize() { return poolSize; }
                public void setPoolSize(int poolSize) { this.poolSize = poolSize; }
            }
            """;

        Files.writeString(javaDir.resolve("DbConfig.java"), javaContent);
        Files.writeString(resourcesDir.resolve("application.yml"), """
            app:
              db:
                url: jdbc:postgresql://prod:5432/db
                pool-size: 20
            """);

        // When
        Map<String, EnvVariable> variables = extractor.extractAllVariables(tempDir);

        // Then
        EnvVariable url = variables.get("APP_DB_URL");
        assertNotNull(url);
        assertEquals("jdbc:postgresql://prod:5432/db", url.getDefaultValue());
        assertFalse(url.isRequired());

        EnvVariable poolSize = variables.get("APP_DB_POOL_SIZE");
        assertNotNull(poolSize);
        assertEquals("20", poolSize.getDefaultValue());
        assertFalse(poolSize.isRequired());
    }

    @Test
    void shouldPreferProfileSpecificConfig() throws IOException {
        // Given
        Path resourcesDir = tempDir.resolve("src/main/resources");
        Files.createDirectories(resourcesDir);

        String baseYaml = """
            app:
              db:
                url: jdbc:postgresql://base:5432/db
            """;
        String profileYaml = """
            app:
              db:
                url: jdbc:postgresql://prod:5432/db
            """;

        Files.writeString(resourcesDir.resolve("application.yml"), baseYaml);
        Files.writeString(resourcesDir.resolve("application-prod.yml"), profileYaml);

        Path javaDir = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(javaDir);
        Files.writeString(javaDir.resolve("DbConfig.java"), """
            package com.example;

            import org.springframework.boot.context.properties.ConfigurationProperties;
            import org.springframework.stereotype.Component;

            @Component
            @ConfigurationProperties(prefix = "app.db")
            public class DbConfig {
                private String url;
                public String getUrl() { return url; }
                public void setUrl(String url) { this.url = url; }
            }
            """);

        // When
        Map<String, EnvVariable> variables = extractor.extractAllVariables(tempDir);

        // Then
        EnvVariable url = variables.get("APP_DB_URL");
        assertNotNull(url);
        assertEquals("jdbc:postgresql://prod:5432/db", url.getDefaultValue());
        assertFalse(url.isRequired());
    }

    @Test
    void shouldExtractVariablesFromProperties() throws IOException {
        // Given
        Path resourcesDir = tempDir.resolve("src/main/resources");
        Files.createDirectories(resourcesDir);

        String propsContent = """
            app.name=MyApp
            app.url=${APP_URL}
            app.secret=${APP_SECRET:defaultSecret}
            """;

        Files.writeString(resourcesDir.resolve("application.properties"), propsContent);

        // When
        Map<String, EnvVariable> variables = extractor.extractAllVariables(tempDir);

        // Then
        assertEquals(2, variables.size());

        EnvVariable appUrl = variables.get("APP_URL");
        assertNotNull(appUrl);
        assertTrue(appUrl.isRequired());
        assertEquals(DefinitionType.APPLICATION_PROPERTIES, appUrl.getDefinition().getType());

        EnvVariable appSecret = variables.get("APP_SECRET");
        assertNotNull(appSecret);
        assertFalse(appSecret.isRequired());
        assertEquals("defaultSecret", appSecret.getDefaultValue());
    }

    @Test
    void shouldExtractLowercaseAndDotVariables() throws IOException {
        // Given
        Path resourcesDir = tempDir.resolve("src/main/resources");
        Files.createDirectories(resourcesDir);

        String yamlContent = """
            app:
              db:
                url: ${db.url}
                user: ${db.user:admin}
            """;

        Files.writeString(resourcesDir.resolve("application.yml"), yamlContent);

        // When
        Map<String, EnvVariable> variables = extractor.extractAllVariables(tempDir);

        // Then
        assertEquals(2, variables.size());
        assertNotNull(variables.get("db.url"));
        assertNotNull(variables.get("db.user"));
        assertEquals("admin", variables.get("db.user").getDefaultValue());
    }

    @Test
    void shouldIgnoreTestAndTargetDirectories() throws IOException {
        // Given
        Path mainDir = tempDir.resolve("src/main/java/com/example");
        Path testDir = tempDir.resolve("src/test/java/com/example");
        Path targetDir = tempDir.resolve("target/classes/com/example");
        Path testResourcesDir = tempDir.resolve("src/test/resources");

        Files.createDirectories(mainDir);
        Files.createDirectories(testDir);
        Files.createDirectories(targetDir);
        Files.createDirectories(testResourcesDir);

        String mainContent = """
            package com.example;
            import org.springframework.beans.factory.annotation.Value;
            public class Main {
                @Value("${MAIN_VAR}")
                private String mainVar;
            }
            """;

        String testContent = """
            package com.example;
            import org.springframework.beans.factory.annotation.Value;
            public class TestClass {
                @Value("${TEST_VAR}")
                private String testVar;
            }
            """;

        Files.writeString(mainDir.resolve("Main.java"), mainContent);
        Files.writeString(testDir.resolve("TestClass.java"), testContent);
        Files.writeString(targetDir.resolve("Generated.java"), testContent);
        Files.writeString(testResourcesDir.resolve("application.yml"), "test:\n  value: ${TEST_CONFIG}");

        // When
        Map<String, EnvVariable> variables = extractor.extractAllVariables(tempDir);

        // Then
        assertEquals(1, variables.size());
        assertNotNull(variables.get("MAIN_VAR"));
        assertNull(variables.get("TEST_VAR"));
        assertNull(variables.get("TEST_CONFIG"));
    }
}
