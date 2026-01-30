package com.example.envdoc.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Результат анализа репозитория.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisResult {
    /**
     * Имя проекта
     */
    private String projectName;

    /**
     * URL репозитория
     */
    private String repositoryUrl;

    /**
     * Ветка, которая была проанализирована
     */
    private String branch;

    /**
     * Время начала анализа
     */
    private LocalDateTime startedAt;

    /**
     * Время завершения анализа
     */
    private LocalDateTime completedAt;

    /**
     * Список найденных переменных окружения
     */
    @Builder.Default
    private List<EnvVariable> variables = new ArrayList<>();

    /**
     * Общее количество переменных
     */
    public int getTotalVariables() {
        return variables != null ? variables.size() : 0;
    }

    /**
     * Количество обязательных переменных
     */
    public long getRequiredVariables() {
        return variables != null ? variables.stream().filter(EnvVariable::isRequired).count() : 0;
    }

    /**
     * Количество опциональных переменных
     */
    public long getOptionalVariables() {
        return variables != null ? variables.stream().filter(v -> !v.isRequired()).count() : 0;
    }

    /**
     * Путь к сгенерированному Markdown файлу
     */
    private String markdownFilePath;

    /**
     * URL страницы в Confluence (если была публикация)
     */
    private String confluencePageUrl;

    /**
     * Сгенерированная Markdown документация
     */
    private String markdownContent;

    /**
     * Ошибки, возникшие при анализе
     */
    @Builder.Default
    private List<String> errors = new ArrayList<>();

    /**
     * Предупреждения
     */
    @Builder.Default
    private List<String> warnings = new ArrayList<>();
}
