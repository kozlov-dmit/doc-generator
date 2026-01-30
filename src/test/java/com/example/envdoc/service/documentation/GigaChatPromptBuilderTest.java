package com.example.envdoc.service.documentation;

import com.example.envdoc.model.DefinitionType;
import com.example.envdoc.model.EnvVariable;
import com.example.envdoc.model.VariableDefinition;
import com.example.envdoc.model.VariableUsage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GigaChatPromptBuilderTest {

    @Test
    void shouldBuildPromptWithJsonAndRequiredTableFormat() {
        GigaChatPromptBuilder builder = new GigaChatPromptBuilder();

        EnvVariable variable = EnvVariable.builder()
                .name("APP_TOKEN")
                .required(true)
                .defaultValue("default-token")
                .description("API token")
                .dataType("string")
                .exampleValue("prod-token")
                .category("Безопасность")
                .definition(VariableDefinition.builder()
                        .type(DefinitionType.SPRING_VALUE)
                        .filePath("src/main/resources/application.yml")
                        .lineNumber(7)
                        .moduleName("module-a")
                        .codeSnippet("token: ${APP_TOKEN}")
                        .build())
                .usages(List.of(VariableUsage.builder()
                        .className("com.example.TokenService")
                        .methodName("getToken")
                        .lineNumber(42)
                        .usageContext("return token;")
                        .build()))
                .build();

        String prompt = builder.buildPrompt(List.of(variable), "test-project");

        assertTrue(prompt.contains("ВХОДНЫЕ ДАННЫЕ В JSON"));
        assertTrue(prompt.contains("\"projectName\":\"test-project\""));
        assertTrue(prompt.contains("\"name\":\"APP_TOKEN\""));
        assertTrue(prompt.contains("Переменная | Описание | Тип | Обязательная | По умолчанию | Пример | Категория | Модуль | Источник | Инициализация | Использования"));
    }
}
