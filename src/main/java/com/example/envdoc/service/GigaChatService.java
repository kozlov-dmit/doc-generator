package com.example.envdoc.service;

import chat.giga.langchain4j.GigaChatChatModel;
import com.example.envdoc.config.GigaChatConfig;
import com.example.envdoc.model.EnvVariable;
import com.example.envdoc.model.VariableUsage;
import com.example.envdoc.tools.ClassCodeTool;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.agentexecutor.AgentExecutor;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.*;

/**
 * Сервис для интеграции с GigaChat API через langchain4j-gigachat.
 */
@Slf4j
@Service
public class GigaChatService {

    private final GigaChatConfig config;
    private final GigaChatChatModel chatModel;
    private final ClassCodeTool classCodeTool;

    public GigaChatService(
            GigaChatConfig config,
            ClassCodeTool classCodeTool,
            Optional<GigaChatChatModel> chatModel) {
        this.config = config;
        this.classCodeTool = classCodeTool;
        this.chatModel = chatModel.orElse(null);
    }

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

        classCodeTool.setRepoPath(repoPath);

        if (chatModel == null || config.getCredentials() == null || config.getCredentials().isBlank()) {
            log.warn("GigaChat not configured, using fallback documentation generation");
            return generateFallbackDocumentation(variables, projectName);
        }

        try {
            String response = generateWithAgent(variables, projectName);

            if (response != null && !response.isBlank()) {
                return response;
            } else {
                log.warn("Empty response from GigaChat, using fallback");
                return generateFallbackDocumentation(variables, projectName);
            }

        } catch (Exception e) {
            log.error("Error calling GigaChat API: {}", e.getMessage(), e);
            return generateFallbackDocumentation(variables, projectName);
        }
    }

    /**
     * Генерирует документацию с использованием AgentExecutor (langgraph4j).
     */
    private String generateWithAgent(List<EnvVariable> variables, String projectName) throws Exception {
        String prompt = buildPrompt(variables, projectName);

        StateGraph<AgentExecutor.State> stateGraph = AgentExecutor.builder()
            .chatModel(chatModel)
            .toolsFromObject(classCodeTool)
            .build();

        CompiledGraph<AgentExecutor.State> agent = stateGraph.compile();

        Map<String, Object> inputs = new HashMap<>();
        inputs.put("messages", List.of(
            SystemMessage.from("Ты — технический писатель, создающий документацию для DevOps команды. " +
                "Отвечай на русском языке. Формат ответа — Markdown. " +
                "Если нужна дополнительная информация о коде классов, используй доступные инструменты."),
            UserMessage.from(prompt)
        ));

        var result = agent.invoke(inputs);
        if (result.isPresent()) {
            var lastMessage = result.get().lastMessage();
            if (lastMessage.isPresent() && lastMessage.get() instanceof AiMessage aiMessage) {
                return aiMessage.text();
            }
        }

        return null;
    }

    /**
     * Генерирует документацию простым вызовом модели (без агента).
     */
    @SuppressWarnings("unused")
    private String generateSimple(List<EnvVariable> variables, String projectName) {
        String prompt = buildPrompt(variables, projectName);

        List<ChatMessage> messages = List.of(
            SystemMessage.from("Ты — технический писатель, создающий документацию для DevOps команды. " +
                "Отвечай на русском языке. Формат ответа — Markdown."),
            UserMessage.from(prompt)
        );

        var response = chatModel.chat(messages);
        if (response != null && response.aiMessage() != null) {
            return response.aiMessage().text();
        }

        return null;
    }

    /**
     * Строит промпт для GigaChat.
     */
    private String buildPrompt(List<EnvVariable> variables, String projectName) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("ЗАДАЧА: Создать документацию по переменным окружения для проекта \"")
              .append(projectName)
              .append("\".\n\n");

        prompt.append("НАЙДЕННЫЕ ПЕРЕМЕННЫЕ:\n\n");

        for (EnvVariable var : variables) {
            prompt.append("## ").append(var.getName()).append("\n");
            prompt.append("- Значение по умолчанию: ")
                  .append(var.getDefaultValue() != null ? var.getDefaultValue() : "не задано")
                  .append("\n");
            prompt.append("- Обязательная: ").append(var.isRequired() ? "Да" : "Нет").append("\n");

            if (var.getDefinition() != null) {
                prompt.append("\n### Где определена:\n");
                prompt.append("- Тип: ").append(var.getDefinition().getType()).append("\n");
                prompt.append("- Файл: ").append(var.getDefinition().getFilePath());
                if (var.getDefinition().getLineNumber() > 0) {
                    prompt.append(":").append(var.getDefinition().getLineNumber());
                }
                prompt.append("\n");
                if (var.getDefinition().getCodeSnippet() != null) {
                    prompt.append("- Код:\n```\n")
                          .append(var.getDefinition().getCodeSnippet())
                          .append("\n```\n");
                }
            }

            if (var.getUsages() != null && !var.getUsages().isEmpty()) {
                prompt.append("\n### Где используется:\n");
                for (VariableUsage usage : var.getUsages()) {
                    prompt.append("- ").append(usage.getClassName())
                          .append(".").append(usage.getMethodName()).append("()");
                    if (usage.getLineNumber() > 0) {
                        prompt.append(" (строка ").append(usage.getLineNumber()).append(")");
                    }
                    prompt.append("\n");
                    if (usage.getUsageContext() != null) {
                        prompt.append("  - Контекст: ").append(usage.getUsageContext()).append("\n");
                    }
                    prompt.append("  - Назначение: ").append(usage.getPurpose()).append("\n");
                }
            }

            prompt.append("\n---\n\n");
        }

        prompt.append("""

                ТРЕБОВАНИЯ К ДОКУМЕНТАЦИИ:
                1. Для каждой переменной сформируй:
                   - Понятное описание назначения (на основе кода и контекста)
                   - Тип данных (string, number, boolean, secret)
                   - Обязательность (required/optional)
                   - Пример значения для production
                   - Где инициализируется
                   - Где используется (список компонентов и их назначение)

                2. Сгруппируй переменные по категориям:
                   - База данных
                   - Безопасность/Аутентификация
                   - Внешние сервисы
                   - Логирование
                   - Прочее

                4. Используй формат Markdown с таблицами для сводной информации.

                Начни документацию с заголовка: # Переменные окружения для проекта {projectName}
                """);

        return prompt.toString();
    }

    /**
     * Генерирует документацию без использования GigaChat (fallback).
     */
    private String generateFallbackDocumentation(List<EnvVariable> variables, String projectName) {
        log.info("Generating fallback documentation without GigaChat");

        StringBuilder doc = new StringBuilder();

        doc.append("# Переменные окружения для проекта ").append(projectName).append("\n\n");

        doc.append("## Сводная таблица\n\n");
        doc.append("| Переменная | Обязательная | По умолчанию | Источник |\n");
        doc.append("|------------|--------------|--------------|----------|\n");

        for (EnvVariable var : variables) {
            doc.append("| `").append(var.getName()).append("` | ");
            doc.append(var.isRequired() ? "Да" : "Нет").append(" | ");
            doc.append(var.getDefaultValue() != null ? "`" + var.getDefaultValue() + "`" : "-").append(" | ");
            doc.append(var.getDefinition() != null ? var.getDefinition().getType() : "-").append(" |\n");
        }

        doc.append("\n---\n\n");

        doc.append("## Детальное описание\n\n");

        for (EnvVariable var : variables) {
            doc.append("### ").append(var.getName()).append("\n\n");

            doc.append("| Параметр | Значение |\n");
            doc.append("|----------|----------|\n");
            doc.append("| Обязательная | ").append(var.isRequired() ? "Да" : "Нет").append(" |\n");
            doc.append("| По умолчанию | ")
               .append(var.getDefaultValue() != null ? "`" + var.getDefaultValue() + "`" : "-")
               .append(" |\n");

            if (var.getDefinition() != null) {
                doc.append("\n#### Где определена\n");
                doc.append("- **Файл:** `").append(var.getDefinition().getFilePath());
                if (var.getDefinition().getLineNumber() > 0) {
                    doc.append(":").append(var.getDefinition().getLineNumber());
                }
                doc.append("`\n");
                doc.append("- **Тип:** ").append(var.getDefinition().getType()).append("\n");

                if (var.getDefinition().getCodeSnippet() != null) {
                    doc.append("\n```\n")
                       .append(var.getDefinition().getCodeSnippet())
                       .append("\n```\n");
                }
            }

            if (var.getUsages() != null && !var.getUsages().isEmpty()) {
                doc.append("\n#### Где используется\n\n");
                doc.append("| Класс | Метод | Назначение |\n");
                doc.append("|-------|-------|------------|\n");

                for (VariableUsage usage : var.getUsages()) {
                    String simpleClassName = usage.getClassName().contains(".")
                            ? usage.getClassName().substring(usage.getClassName().lastIndexOf('.') + 1)
                            : usage.getClassName();

                    doc.append("| `").append(simpleClassName).append("` | ");
                    doc.append("`").append(usage.getMethodName()).append("()` | ");
                    doc.append(usage.getPurpose()).append(" |\n");
                }
            }

            doc.append("\n---\n\n");
        }

        doc.append("## Примеры конфигурации\n\n");

        doc.append("### Docker Compose\n\n");
        doc.append("```yaml\nservices:\n  app:\n    environment:\n");
        for (EnvVariable var : variables) {
            if (var.isRequired()) {
                doc.append("      - ").append(var.getName()).append("=${").append(var.getName()).append("}\n");
            }
        }
        doc.append("```\n\n");

        doc.append("### Kubernetes Secret\n\n");
        doc.append("```yaml\napiVersion: v1\nkind: Secret\nmetadata:\n  name: app-secrets\nstringData:\n");
        for (EnvVariable var : variables) {
            if (var.isRequired()) {
                doc.append("  ").append(var.getName()).append(": \"<value>\"\n");
            }
        }
        doc.append("```\n\n");

        doc.append("### .env (локальная разработка)\n\n");
        doc.append("```env\n");
        for (EnvVariable var : variables) {
            doc.append(var.getName()).append("=");
            if (var.getDefaultValue() != null) {
                doc.append(var.getDefaultValue());
            } else {
                doc.append("<value>");
            }
            doc.append("\n");
        }
        doc.append("```\n");

        return doc.toString();
    }
}
