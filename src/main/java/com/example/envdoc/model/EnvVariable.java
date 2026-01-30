package com.example.envdoc.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Модель переменной окружения с полной информацией о её определении и использовании.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnvVariable {
    /**
     * Имя переменной окружения (например: DATABASE_URL)
     */
    private String name;

    /**
     * Значение по умолчанию (если задано)
     */
    private String defaultValue;

    /**
     * Является ли переменная обязательной
     */
    private boolean required;

    /**
     * Информация о том, где определена переменная
     */
    private VariableDefinition definition;

    /**
     * Список мест, где используется переменная
     */
    @Builder.Default
    private List<VariableUsage> usages = new ArrayList<>();

    /**
     * Сгенерированное описание переменной (от GigaChat)
     */
    private String description;

    /**
     * Тип данных переменной (string, number, boolean, secret)
     */
    private String dataType;

    /**
     * Категория переменной (DATABASE, SECURITY, API, etc.)
     */
    private String category;

    /**
     * Пример значения
     */
    private String exampleValue;

    /**
     * Добавить информацию об использовании переменной
     */
    public void addUsage(VariableUsage usage) {
        if (this.usages == null) {
            this.usages = new ArrayList<>();
        }
        this.usages.add(usage);
    }
}
