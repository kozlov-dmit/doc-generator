package com.example.envdoc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация для Confluence API.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "confluence")
public class ConfluenceConfig {
    /**
     * Включена ли интеграция с Confluence
     */
    private boolean enabled = false;

    /**
     * Базовый URL Confluence
     */
    private String baseUrl;

    /**
     * Имя пользователя
     */
    private String username;

    /**
     * API токен
     */
    private String token;

    /**
     * Ключ пространства по умолчанию
     */
    private String spaceKey;

    /**
     * Таймаут запросов в секундах
     */
    private int timeoutSeconds = 30;
}
