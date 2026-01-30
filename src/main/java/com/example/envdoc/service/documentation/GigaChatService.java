package com.example.envdoc.service.documentation;

import com.example.envdoc.config.GigaChatConfig;
import com.example.envdoc.model.EnvVariable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

/**
 * Сервис для интеграции с GigaChat API через langchain4j-gigachat.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GigaChatService {

    private final GigaChatConfig config;
    private final GigaChatAgentDocumentationService agentService;
    private final GigaChatSimpleDocumentationService simpleService;
    private final GigaChatFallbackDocumentationService fallbackService;

    /**
     * Генерирует документацию для переменных окружения с использованием GigaChat.
     *
     * @param variables список переменных окружения
     * @param projectName имя проекта
     * @param repoPath путь к репозиторию (для ClassCodeTool)
     * @return сгенерированная документация в формате Markdown
     */
    public String generateDocumentation(List<EnvVariable> variables, String projectName, Path repoPath) {
        log.info("Generating documentation with GigaChat for {} variables", variables.size());

        if (config.getCredentials() == null || config.getCredentials().isBlank()) {
            log.warn("GigaChat not configured, using fallback documentation generation");
            return fallbackService.generateDocumentation(variables, projectName);
        }

        try {
            String response = agentService.generateDocumentation(variables, projectName, repoPath);

            if (response != null && !response.isBlank()) {
                return response;
            }
            log.warn("Empty response from GigaChat agent, trying simple client");
            response = simpleService.generateDocumentation(variables, projectName);
            if (response != null && !response.isBlank()) {
                return response;
            }
            log.warn("Empty response from GigaChat, using fallback");
            return fallbackService.generateDocumentation(variables, projectName);

        } catch (Exception e) {
            log.error("Error calling GigaChat API: {}", e.getMessage(), e);
            return fallbackService.generateDocumentation(variables, projectName);
        }
    }
}
