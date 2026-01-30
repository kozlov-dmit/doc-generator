package com.example.envdoc.dto;

import com.example.envdoc.model.DefinitionType;
import com.example.envdoc.model.UsagePurpose;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO переменной окружения для API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnvVariableDto {
    /**
     * Имя переменной
     */
    private String name;

    /**
     * Описание переменной
     */
    private String description;

    /**
     * Тип данных (string, number, boolean, secret)
     */
    private String type;

    /**
     * Обязательность
     */
    private boolean required;

    /**
     * Значение по умолчанию
     */
    private String defaultValue;

    /**
     * Пример значения
     */
    private String example;

    /**
     * Категория (DATABASE, SECURITY, API, etc.)
     */
    private String category;

    /**
     * Информация об определении
     */
    private DefinitionDto definition;

    /**
     * Список использований
     */
    private List<UsageDto> usages;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DefinitionDto {
        private DefinitionType type;
        private String filePath;
        private int lineNumber;
        private String codeSnippet;
        private String moduleName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UsageDto {
        private String className;
        private String methodName;
        private int lineNumber;
        private UsagePurpose purpose;
        private String context;
    }
}
