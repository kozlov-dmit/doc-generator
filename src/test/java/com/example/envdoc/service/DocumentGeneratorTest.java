package com.example.envdoc.service;

import com.example.envdoc.config.OutputConfig;
import com.example.envdoc.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DocumentGeneratorTest {

    private DocumentGenerator generator;
    private OutputConfig outputConfig;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        outputConfig = new OutputConfig();
        outputConfig.getMarkdown().setPath(tempDir.toString());
        generator = new DocumentGenerator(outputConfig);
    }

    @Test
    void shouldGenerateBasicDocumentation() {
        // Given
        EnvVariable dbUrl = EnvVariable.builder()
                .name("DATABASE_URL")
                .required(true)
                .definition(VariableDefinition.builder()
                        .type(DefinitionType.APPLICATION_YAML)
                        .filePath("src/main/resources/application.yml")
                        .lineNumber(12)
                        .codeSnippet("url: ${DATABASE_URL}")
                        .build())
                .usages(List.of(
                        VariableUsage.builder()
                                .className("com.example.config.DataSourceConfig")
                                .methodName("dataSource")
                                .purpose(UsagePurpose.DATABASE_CONNECTION)
                                .build()
                ))
                .build();

        EnvVariable apiKey = EnvVariable.builder()
                .name("API_KEY")
                .required(true)
                .defaultValue(null)
                .definition(VariableDefinition.builder()
                        .type(DefinitionType.SPRING_VALUE)
                        .filePath("src/main/java/com/example/ApiClient.java")
                        .lineNumber(15)
                        .codeSnippet("@Value(\"${API_KEY}\")")
                        .build())
                .usages(List.of(
                        VariableUsage.builder()
                                .className("com.example.ApiClient")
                                .methodName("callExternalApi")
                                .purpose(UsagePurpose.EXTERNAL_API)
                                .build()
                ))
                .build();

        AnalysisResult result = AnalysisResult.builder()
                .projectName("test-project")
                .repositoryUrl("https://github.com/test/repo")
                .branch("main")
                .startedAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .variables(List.of(dbUrl, apiKey))
                .build();

        // When
        String markdown = generator.generate(result);

        // Then
        assertNotNull(markdown);
        assertTrue(markdown.contains("# Переменные окружения для проекта test-project"));
        assertTrue(markdown.contains("DATABASE_URL"));
        assertTrue(markdown.contains("API_KEY"));
        assertTrue(markdown.contains("## Сводная таблица"));
        assertTrue(markdown.contains("## Детальное описание"));
        assertTrue(markdown.contains("## Примеры конфигурации"));
        assertTrue(markdown.contains("### Docker Compose"));
        assertTrue(markdown.contains("### Kubernetes Secret"));
        assertTrue(markdown.contains("### .env"));
    }

    @Test
    void shouldCategorizeVariablesCorrectly() {
        // Given
        EnvVariable dbVar = EnvVariable.builder()
                .name("DATABASE_URL")
                .required(true)
                .usages(List.of())
                .build();

        EnvVariable authVar = EnvVariable.builder()
                .name("JWT_SECRET")
                .required(true)
                .usages(List.of())
                .build();

        EnvVariable logVar = EnvVariable.builder()
                .name("LOG_LEVEL")
                .required(false)
                .defaultValue("INFO")
                .usages(List.of())
                .build();

        AnalysisResult result = AnalysisResult.builder()
                .projectName("test-project")
                .variables(List.of(dbVar, authVar, logVar))
                .build();

        // When
        String markdown = generator.generate(result);

        // Then
        assertTrue(markdown.contains("База данных"));
        assertTrue(markdown.contains("Безопасность"));
        assertTrue(markdown.contains("Логирование"));
    }

    @Test
    void shouldSaveDocumentationToFile() {
        // Given
        EnvVariable var = EnvVariable.builder()
                .name("TEST_VAR")
                .required(true)
                .usages(List.of())
                .build();

        AnalysisResult result = AnalysisResult.builder()
                .projectName("test-project")
                .variables(List.of(var))
                .markdownContent("# Test content")
                .build();

        // When
        Path outputPath = generator.generateAndSave(result);

        // Then
        assertNotNull(outputPath);
        assertTrue(outputPath.toFile().exists());
        assertTrue(outputPath.toString().endsWith(".md"));
    }

    @Test
    void shouldUseProvidedMarkdownContent() {
        // Given
        String customContent = "# Custom documentation\n\nThis is custom content.";

        AnalysisResult result = AnalysisResult.builder()
                .projectName("test-project")
                .markdownContent(customContent)
                .variables(List.of())
                .build();

        // When
        String markdown = generator.generate(result);

        // Then
        assertEquals(customContent, markdown);
    }

    @Test
    void shouldGenerateDockerComposeExample() {
        // Given
        EnvVariable required = EnvVariable.builder()
                .name("REQUIRED_VAR")
                .required(true)
                .usages(List.of())
                .build();

        EnvVariable optional = EnvVariable.builder()
                .name("OPTIONAL_VAR")
                .required(false)
                .defaultValue("default")
                .usages(List.of())
                .build();

        AnalysisResult result = AnalysisResult.builder()
                .projectName("test-project")
                .variables(List.of(required, optional))
                .build();

        // When
        String markdown = generator.generate(result);

        // Then
        assertTrue(markdown.contains("services:"));
        assertTrue(markdown.contains("REQUIRED_VAR"));
        assertTrue(markdown.contains("OPTIONAL_VAR"));
    }

    @Test
    void shouldGenerateKubernetesSecretExample() {
        // Given
        EnvVariable secretVar = EnvVariable.builder()
                .name("DB_PASSWORD")
                .required(true)
                .usages(List.of())
                .build();

        AnalysisResult result = AnalysisResult.builder()
                .projectName("my-service")
                .variables(List.of(secretVar))
                .build();

        // When
        String markdown = generator.generate(result);

        // Then
        assertTrue(markdown.contains("apiVersion: v1"));
        assertTrue(markdown.contains("kind: Secret"));
        assertTrue(markdown.contains("DB_PASSWORD"));
    }
}
