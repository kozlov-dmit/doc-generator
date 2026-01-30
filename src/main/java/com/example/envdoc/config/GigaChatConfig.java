package com.example.envdoc.config;

import chat.giga.client.auth.AuthClient;
import chat.giga.client.auth.AuthClientBuilder;
import chat.giga.http.client.JdkHttpClientBuilder;
import chat.giga.http.client.SSL;
import chat.giga.langchain4j.GigaChatChatModel;
import chat.giga.langchain4j.GigaChatChatRequestParameters;
import chat.giga.model.Scope;
import com.example.envdoc.metrics.GigaChatMetricsListener;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Конфигурация для GigaChat API через langchain4j-gigachat.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "gigachat")
public class GigaChatConfig {
    /**
     * URL для API запросов
     */
    private String apiUrl = "https://gigachat.devices.sberbank.ru/api/v1";

    /**
     * URL для OAuth аутентификации
     */
    private String authUrl = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth";

    /**
     * Credentials для авторизации (Client ID:Client Secret в Base64)
     */
    private String credentials;

    /**
     * Scope доступа (GIGACHAT_API_PERS или GIGACHAT_API_CORP)
     */
    private String scope = "GIGACHAT_API_PERS";

    /**
     * Модель GigaChat для использования
     */
    private String model = "GigaChat-Pro";

    /**
     * Температура для генерации (0.0 - 1.0)
     */
    private double temperature = 0.3;

    /**
     * Максимальное количество токенов в ответе
     */
    private int maxTokens = 4096;

    /**
     * Таймаут запроса в секундах
     */
    private int timeoutSeconds = 120;

    /**
     * Проверять SSL сертификаты
     */
    private boolean verifySsl = false;

    /**
     * Тип keystore (например, PKCS12, JKS)
     */
    private String keystoreType;

    /**
     * Путь к keystore
     */
    private String keystorePath;

    /**
     * Пароль keystore
     */
    private String keystorePassword;

    /**
     * Тип truststore (например, PKCS12, JKS)
     */
    private String truststoreType;

    /**
     * Путь к truststore
     */
    private String truststorePath;

    /**
     * Пароль truststore
     */
    private String truststorePassword;

    /**
     * Создаёт бин GigaChatChatModel для использования в сервисах.
     */
    @Bean
    public GigaChatChatModel gigaChatChatModel(MeterRegistry meterRegistry) {
        if (hasKeyStoreConfigured()) {
            var httpClient = buildCertificateHttpClient();
            AuthClient authClient = AuthClient.builder()
                .withCertificatesAuth(httpClient)
                .build();

            return GigaChatChatModel.builder()
                .apiHttpClient(httpClient)
                .authClient(authClient)
                .apiUrl(apiUrl)
                .verifySslCerts(verifySsl)
                .defaultChatRequestParameters(GigaChatChatRequestParameters.builder()
                    .modelName(model)
                    .temperature(temperature)
                    .maxOutputTokens(maxTokens)
                    .build())
                .logRequests(true)
                .logResponses(true)
                .listeners(List.of(new GigaChatMetricsListener(meterRegistry)))
                .build();
        }

        if (credentials == null || credentials.isBlank()) {
            return null;
        }

        Scope gigaScope = "GIGACHAT_API_CORP".equals(scope)
            ? Scope.GIGACHAT_API_CORP
            : Scope.GIGACHAT_API_PERS;

        AuthClient authClient = AuthClient.builder()
            .withOAuth(AuthClientBuilder.OAuthBuilder.builder()
                .scope(gigaScope)
                .authKey(credentials)
                .authApiUrl(authUrl)
                .verifySslCerts(verifySsl)
                .build())
            .build();

        return GigaChatChatModel.builder()
            .authClient(authClient)
            .apiUrl(apiUrl)
            .verifySslCerts(verifySsl)
            .defaultChatRequestParameters(GigaChatChatRequestParameters.builder()
                .modelName(model)
                .temperature(temperature)
                .maxOutputTokens(maxTokens)
                .build())
            .logRequests(true)
            .logResponses(true)
            .listeners(List.of(new GigaChatMetricsListener(meterRegistry)))
            .build();
    }

    private boolean hasKeyStoreConfigured() {
        return keystorePath != null && !keystorePath.isBlank();
    }

    private chat.giga.http.client.HttpClient buildCertificateHttpClient() {
        if (keystorePath == null || keystorePath.isBlank()) {
            throw new IllegalArgumentException("Certificate auth требует keystorePath");
        }

        SSL.SSLBuilder sslBuilder = SSL.builder()
            .keystorePath(keystorePath)
            .keystorePassword(keystorePassword)
            .keystoreType(keystoreType != null ? keystoreType : "PKCS12")
            .verifySslCerts(verifySsl);

        if (truststorePath != null && !truststorePath.isBlank()) {
            sslBuilder
                .truststorePath(truststorePath)
                .truststorePassword(truststorePassword)
                .trustStoreType(truststoreType != null ? truststoreType : "PKCS12");
        }

        SSL ssl = sslBuilder.build();

        return new JdkHttpClientBuilder()
            .ssl(ssl)
            .build();
    }
}
