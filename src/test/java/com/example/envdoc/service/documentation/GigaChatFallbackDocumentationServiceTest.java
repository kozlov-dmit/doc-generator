package com.example.envdoc.service.documentation;

import com.example.envdoc.model.DefinitionType;
import com.example.envdoc.model.EnvVariable;
import com.example.envdoc.model.VariableDefinition;
import com.example.envdoc.model.VariableUsage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GigaChatFallbackDocumentationServiceTest {

    @Test
    void shouldGenerateSingleTableWithAllColumns() {
        GigaChatFallbackDocumentationService service = new GigaChatFallbackDocumentationService();

        EnvVariable variable = EnvVariable.builder()
                .name("DB_URL")
                .required(true)
                .defaultValue("jdbc:postgresql://localhost/db")
                .description("Database URL")
                .dataType("string")
                .exampleValue("jdbc:postgresql://prod/db")
                .category("База данных")
                .definition(VariableDefinition.builder()
                        .type(DefinitionType.APPLICATION_YAML)
                        .filePath("src/main/resources/application.yml")
                        .lineNumber(12)
                        .moduleName("root")
                        .build())
                .usages(List.of(VariableUsage.builder()
                        .className("com.example.DbConfig")
                        .methodName("dataSource")
                        .build()))
                .build();

        String markdown = service.generateDocumentation(List.of(variable), "test-project");

        assertTrue(markdown.contains("# Переменные окружения для проекта test-project"));
        assertTrue(markdown.contains("| Переменная | Описание | Тип | Обязательная | По умолчанию | Пример | Категория | Модуль | Источник | Инициализация | Использования |"));
        assertTrue(markdown.contains("`DB_URL`"));
        assertTrue(markdown.contains("`src/main/resources/application.yml:12`"));
        assertTrue(markdown.contains("APPLICATION_YAML"));
    }
}
