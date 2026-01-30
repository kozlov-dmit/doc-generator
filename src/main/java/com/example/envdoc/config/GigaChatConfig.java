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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
     * Путь к CA сертификату (PEM)
     */
    private String caPem;

    /**
     * Путь к клиентскому сертификату (PEM)
     */
    private String certPem;

    /**
     * Путь к приватному ключу (PEM, PKCS8)
     */
    private String keyPem;

    /**
     * Пароль приватного ключа (если есть)
     */
    private String keyPassword;

    /**
     * Создаёт бин GigaChatChatModel для использования в сервисах.
     */
    @Bean
    public GigaChatChatModel gigaChatChatModel(MeterRegistry meterRegistry) {
        if (hasCertificateAuthConfigured()) {
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

    private boolean hasCertificateAuthConfigured() {
        return (certPem != null && !certPem.isBlank())
            || (keyPem != null && !keyPem.isBlank());
    }

    private chat.giga.http.client.HttpClient buildCertificateHttpClient() {
        if (certPem == null || certPem.isBlank() || keyPem == null || keyPem.isBlank()) {
            throw new IllegalArgumentException("Certificate auth требует certPem и keyPem");
        }

        try {
            String password = keyPassword != null ? keyPassword : "";

            Path keyStorePath = createKeyStore(certPem, keyPem, password);
            Path trustStorePath = caPem != null && !caPem.isBlank()
                ? createTrustStore(caPem, password)
                : null;

            SSL ssl;
            if (trustStorePath != null) {
                ssl = SSL.builder()
                    .keystorePath(keyStorePath.toString())
                    .keystorePassword(password)
                    .keystoreType("PKCS12")
                    .truststorePath(trustStorePath.toString())
                    .truststorePassword(password)
                    .trustStoreType("PKCS12")
                    .verifySslCerts(verifySsl)
                    .build();
            } else {
                ssl = SSL.builder()
                    .keystorePath(keyStorePath.toString())
                    .keystorePassword(password)
                    .keystoreType("PKCS12")
                    .verifySslCerts(verifySsl)
                    .build();
            }

            return new JdkHttpClientBuilder()
                .ssl(ssl)
                .build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize GigaChat with certificates", e);
        }
    }

    private Path createKeyStore(String certPath, String keyPath, String password) throws Exception {
        List<Certificate> certificates = readCertificates(certPath);
        if (certificates.isEmpty()) {
            throw new IllegalArgumentException("Certificate file is empty: " + certPath);
        }
        PrivateKey privateKey = readPrivateKey(keyPath);

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry("gigachat", privateKey, password.toCharArray(),
            certificates.toArray(new Certificate[0]));

        Path keyStoreFile = Files.createTempFile("gigachat-keystore", ".p12");
        keyStoreFile.toFile().deleteOnExit();
        try (var out = Files.newOutputStream(keyStoreFile)) {
            keyStore.store(out, password.toCharArray());
        }
        return keyStoreFile;
    }

    private Path createTrustStore(String caPath, String password) throws Exception {
        List<Certificate> certificates = readCertificates(caPath);
        if (certificates.isEmpty()) {
            throw new IllegalArgumentException("CA file is empty: " + caPath);
        }

        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        trustStore.load(null, null);
        int index = 0;
        for (Certificate cert : certificates) {
            trustStore.setCertificateEntry("ca-" + index++, cert);
        }

        Path trustStoreFile = Files.createTempFile("gigachat-truststore", ".p12");
        trustStoreFile.toFile().deleteOnExit();
        try (var out = Files.newOutputStream(trustStoreFile)) {
            trustStore.store(out, password.toCharArray());
        }
        return trustStoreFile;
    }

    private List<Certificate> readCertificates(String path) throws Exception {
        String pem = Files.readString(Path.of(path), StandardCharsets.UTF_8);
        Pattern pattern = Pattern.compile(
            "-----BEGIN CERTIFICATE-----([^-]+)-----END CERTIFICATE-----",
            Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(pem);
        List<Certificate> certificates = new ArrayList<>();
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        while (matcher.find()) {
            String base64 = matcher.group(1).replaceAll("\\s+", "");
            byte[] decoded = Base64.getDecoder().decode(base64);
            try (var in = new ByteArrayInputStream(decoded)) {
                certificates.add(factory.generateCertificate(in));
            }
        }
        return certificates;
    }

    private PrivateKey readPrivateKey(String path) throws Exception {
        String pem = Files.readString(Path.of(path), StandardCharsets.UTF_8);
        if (pem.contains("BEGIN RSA PRIVATE KEY")) {
            throw new IllegalArgumentException("PKCS1 ключ не поддерживается, используйте PKCS8");
        }

        String sanitized = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s+", "");
        byte[] decoded = Base64.getDecoder().decode(sanitized);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);

        try {
            return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
        } catch (Exception ignored) {
            return KeyFactory.getInstance("EC").generatePrivate(keySpec);
        }
    }
}
