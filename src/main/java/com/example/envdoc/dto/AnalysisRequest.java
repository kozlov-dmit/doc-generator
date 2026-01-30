package com.example.envdoc.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Запрос на анализ репозитория.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisRequest {
    /**
     * URL репозитория (BitBucket, GitHub, GitLab)
     */
    @NotBlank(message = "Repository URL is required")
    private String repositoryUrl;

    /**
     * Ветка для анализа (по умолчанию main или master)
     */
    private String branch;

    /**
     * Токен для доступа к репозиторию (опционально, если не задан глобально)
     */
    private String bitbucketToken;

    /**
     * Форматы вывода (MARKDOWN, CONFLUENCE)
     */
    private List<OutputFormat> outputFormats;

    /**
     * Конфигурация для Confluence (если выбран формат CONFLUENCE)
     */
    private ConfluenceConfig confluenceConfig;

    public enum OutputFormat {
        MARKDOWN,
        CONFLUENCE
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfluenceConfig {
        /**
         * Ключ пространства Confluence
         */
        private String spaceKey;

        /**
         * ID родительской страницы
         */
        private String parentPageId;

        /**
         * Заголовок страницы (опционально)
         */
        private String pageTitle;
    }
}
