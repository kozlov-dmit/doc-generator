package com.example.envdoc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация вывода результатов.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "output")
public class OutputConfig {

    private MarkdownConfig markdown = new MarkdownConfig();

    @Data
    public static class MarkdownConfig {
        /**
         * Включён ли вывод в Markdown
         */
        private boolean enabled = true;

        /**
         * Путь для сохранения Markdown файлов
         */
        private String path = "./output";

        /**
         * Имя файла по умолчанию
         */
        private String defaultFilename = "ENV_VARIABLES.md";
    }
}
