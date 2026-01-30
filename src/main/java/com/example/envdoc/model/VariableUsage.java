package com.example.envdoc.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Информация об использовании переменной окружения в коде.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VariableUsage {
    /**
     * Полное имя класса
     */
    private String className;

    /**
     * Имя метода, где используется переменная
     */
    private String methodName;

    /**
     * Номер строки
     */
    private int lineNumber;

    /**
     * Путь к файлу
     */
    private String filePath;

    /**
     * Контекст использования (описание)
     */
    private String usageContext;

    /**
     * Цель использования
     */
    private UsagePurpose purpose;

    /**
     * Фрагмент кода с использованием
     */
    private String codeSnippet;
}
