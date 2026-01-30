package com.example.envdoc.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Информация о том, где и как определена переменная окружения.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VariableDefinition {
    /**
     * Тип определения (YAML, @Value, System.getenv и т.д.)
     */
    private DefinitionType type;

    /**
     * Путь к файлу относительно корня репозитория
     */
    private String filePath;

    /**
     * Номер строки в файле
     */
    private int lineNumber;

    /**
     * Фрагмент кода/конфига с определением переменной
     */
    private String codeSnippet;

    /**
     * Полное имя класса (для Java файлов)
     */
    private String className;

    /**
     * Имя поля или метода, где определена переменная
     */
    private String fieldOrMethodName;

    /**
     * Имя модуля, в котором найдено определение
     */
    private String moduleName;
}
