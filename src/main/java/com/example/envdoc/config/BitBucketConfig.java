package com.example.envdoc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация для доступа к BitBucket.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "bitbucket")
public class BitBucketConfig {
    /**
     * Токен для аутентификации (App Password)
     */
    private String token;

    /**
     * Имя пользователя (для App Password)
     */
    private String username;

    /**
     * Таймаут клонирования в секундах
     */
    private int cloneTimeoutSeconds = 300;
}
