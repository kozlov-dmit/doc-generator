package com.example.envdoc.model;

/**
 * Цель использования переменной окружения.
 */
public enum UsagePurpose {
    /**
     * Подключение к базе данных
     */
    DATABASE_CONNECTION,

    /**
     * Вызов внешнего API
     */
    EXTERNAL_API,

    /**
     * Аутентификация и безопасность
     */
    AUTHENTICATION,

    /**
     * Feature toggle / флаг функциональности
     */
    FEATURE_FLAG,

    /**
     * Конфигурация логирования
     */
    LOGGING_CONFIG,

    /**
     * Конфигурация кэша
     */
    CACHE_CONFIG,

    /**
     * Конфигурация сервера
     */
    SERVER_CONFIG,

    /**
     * Конфигурация очередей сообщений
     */
    MESSAGING_CONFIG,

    /**
     * Прочее
     */
    OTHER
}
