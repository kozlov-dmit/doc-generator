package com.example.envdoc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Основная конфигурация приложения.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppConfig {
    /**
     * Временная директория для клонирования репозиториев
     */
    private String tempDir = System.getProperty("java.io.tmpdir") + "/env-doc-agent";

    /**
     * Максимальное время хранения клонированных репозиториев (в минутах)
     */
    private int repoRetentionMinutes = 60;

    /**
     * Максимальный размер репозитория для клонирования (в MB)
     */
    private int maxRepoSizeMb = 500;
}
