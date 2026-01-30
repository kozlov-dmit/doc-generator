package com.example.envdoc.model;

/**
 * Тип определения переменной окружения в исходном коде.
 */
public enum DefinitionType {
    /**
     * Определена в application.yml или application.yaml
     */
    APPLICATION_YAML,

    /**
     * Определена в application.properties
     */
    APPLICATION_PROPERTIES,

    /**
     * Определена через аннотацию @Value("${VAR}")
     */
    SPRING_VALUE,

    /**
     * Определена через @ConfigurationProperties
     */
    CONFIG_PROPERTIES,

    /**
     * Прямой вызов System.getenv("VAR")
     */
    SYSTEM_GETENV,

    /**
     * Прямой вызов System.getProperty("VAR")
     */
    SYSTEM_PROPERTY,

    /**
     * Через Spring Environment API: environment.getProperty("VAR")
     */
    ENVIRONMENT_API
}
