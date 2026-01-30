package com.example.envdoc.service;

import com.example.envdoc.config.OutputConfig;
import com.example.envdoc.model.AnalysisResult;
import com.example.envdoc.model.EnvVariable;
import com.example.envdoc.model.VariableUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис для генерации Markdown документации.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentGenerator {

    private final OutputConfig outputConfig;

    /**
     * Генерирует Markdown документацию и сохраняет в файл.
     *
     * @param result результат анализа с переменными
     * @return путь к сохранённому файлу
     */
    public Path generateAndSave(AnalysisResult result) {
        String markdown = generate(result);

        try {
            Path outputDir = Path.of(outputConfig.getMarkdown().getPath());
            Files.createDirectories(outputDir);

            String filename = result.getProjectName() + "_" + outputConfig.getMarkdown().getDefaultFilename();
            Path outputFile = outputDir.resolve(filename);

            Files.writeString(outputFile, markdown);
            log.info("Documentation saved to: {}", outputFile);

            return outputFile;

        } catch (IOException e) {
            log.error("Error saving documentation", e);
            throw new RuntimeException("Failed to save documentation: " + e.getMessage(), e);
        }
    }

    /**
     * Генерирует Markdown документацию.
     *
     * @param result результат анализа
     * @return Markdown строка
     */
    public String generate(AnalysisResult result) {
        // Если есть сгенерированный контент от GigaChat - возвращаем его
        if (result.getMarkdownContent() != null && !result.getMarkdownContent().isBlank()) {
            return result.getMarkdownContent();
        }

        // Иначе генерируем базовую документацию
        return generateBasicDocumentation(result);
    }

    /**
     * Генерирует базовую документацию без использования AI.
     */
    private String generateBasicDocumentation(AnalysisResult result) {
        StringBuilder doc = new StringBuilder();

        doc.append("# Переменные окружения для проекта ").append(result.getProjectName()).append("\n\n");

        // Метаданные
        doc.append("> Документация сгенерирована автоматически\n");
        doc.append("> \n");
        doc.append("> - **Репозиторий:** ").append(result.getRepositoryUrl()).append("\n");
        doc.append("> - **Ветка:** ").append(result.getBranch() != null ? result.getBranch() : "default").append("\n");
        doc.append("> - **Всего переменных:** ").append(result.getTotalVariables()).append("\n");
        doc.append("> - **Обязательных:** ").append(result.getRequiredVariables()).append("\n");
        doc.append("> - **Опциональных:** ").append(result.getOptionalVariables()).append("\n\n");

        // Группировка по категориям
        List<EnvVariable> sortedVariables = sortVariables(result.getVariables());
        Map<String, List<EnvVariable>> categorized = categorizeVariables(sortedVariables);

        // Сводная таблица
        doc.append("## Сводная таблица\n\n");
        doc.append("| Переменная | Тип | Обязательная | По умолчанию | Категория | Источник | Описание |\n");
        doc.append("|------------|-----|--------------|-------------|-----------|----------|----------|\n");

        for (EnvVariable var : sortedVariables) {
            doc.append("| `").append(var.getName()).append("` | ");
            doc.append(var.getDataType() != null ? var.getDataType() : "string").append(" | ");
            doc.append(var.isRequired() ? "Да" : "Нет").append(" | ");
            doc.append(formatDefaultValueCell(var.getDefaultValue())).append(" | ");
            doc.append(escapeMarkdownTableCell(var.getCategory() != null ? var.getCategory() : detectCategory(var)))
               .append(" | ");
            doc.append(var.getDefinition() != null ? var.getDefinition().getType() : "-").append(" | ");
            doc.append(escapeMarkdownTableCell(var.getDescription() != null ? var.getDescription() : "-"))
               .append(" |\n");
        }

        doc.append("\n---\n\n");

        // Детальное описание по категориям
        doc.append("## Детальное описание\n\n");

        for (Map.Entry<String, List<EnvVariable>> entry : categorized.entrySet()) {
            doc.append("### ").append(entry.getKey()).append("\n\n");

            for (EnvVariable var : entry.getValue()) {
                doc.append("#### ").append(var.getName()).append("\n\n");

                if (var.getDescription() != null && !var.getDescription().isBlank()) {
                    doc.append("**Описание:** ").append(var.getDescription()).append("\n\n");
                }

                doc.append("| Параметр | Значение |\n");
                doc.append("|----------|----------|\n");
                doc.append("| Тип | `").append(var.getDataType() != null ? var.getDataType() : "string").append("` |\n");
                doc.append("| Обязательная | ").append(var.isRequired() ? "Да" : "Нет").append(" |\n");
                doc.append("| По умолчанию | ")
                   .append(var.getDefaultValue() != null ? "`" + var.getDefaultValue() + "`" : "-")
                   .append(" |\n");
                if (var.getExampleValue() != null) {
                    doc.append("| Пример | `").append(var.getExampleValue()).append("` |\n");
                }

                // Где определена
                if (var.getDefinition() != null) {
                    doc.append("\n**Где определена (инициализация)**\n\n");
                    doc.append("- **Файл:** `").append(var.getDefinition().getFilePath());
                    if (var.getDefinition().getLineNumber() > 0) {
                        doc.append(":").append(var.getDefinition().getLineNumber());
                    }
                    doc.append("`\n");
                    doc.append("- **Тип:** ").append(var.getDefinition().getType()).append("\n");

                    if (var.getDefinition().getCodeSnippet() != null &&
                        !var.getDefinition().getCodeSnippet().isBlank()) {
                        doc.append("\n```");
                        // Определяем язык по типу файла
                        if (var.getDefinition().getFilePath().endsWith(".java")) {
                            doc.append("java");
                        } else if (var.getDefinition().getFilePath().endsWith(".yml") ||
                                   var.getDefinition().getFilePath().endsWith(".yaml")) {
                            doc.append("yaml");
                        } else if (var.getDefinition().getFilePath().endsWith(".properties")) {
                            doc.append("properties");
                        }
                        doc.append("\n");
                        doc.append(var.getDefinition().getCodeSnippet());
                        doc.append("\n```\n");
                    }
                }

                // Где используется
                if (var.getUsages() != null && !var.getUsages().isEmpty()) {
                    doc.append("\n**Где используется**\n\n");
                    doc.append("| Класс | Метод | Назначение |\n");
                    doc.append("|-------|-------|------------|\n");

                    for (VariableUsage usage : var.getUsages()) {
                        String simpleClassName = usage.getClassName().contains(".")
                                ? usage.getClassName().substring(usage.getClassName().lastIndexOf('.') + 1)
                                : usage.getClassName();

                        doc.append("| `").append(simpleClassName).append("` | ");
                        doc.append("`").append(usage.getMethodName()).append("()` | ");
                        doc.append(usage.getPurpose() != null ? usage.getPurpose() : "-").append(" |\n");
                    }
                }

                doc.append("\n---\n\n");
            }
        }

        // Примеры конфигурации
        doc.append("## Примеры конфигурации\n\n");

        // Docker Compose
        doc.append("### Docker Compose\n\n");
        doc.append("```yaml\nservices:\n  app:\n    environment:\n");
        for (EnvVariable var : sortedVariables) {
            doc.append("      - ").append(var.getName()).append("=");
            if (var.getExampleValue() != null) {
                doc.append(var.getExampleValue());
            } else if (var.getDefaultValue() != null) {
                doc.append(var.getDefaultValue());
            } else {
                doc.append("${").append(var.getName()).append("}");
            }
            doc.append("\n");
        }
        doc.append("```\n\n");

        // Kubernetes Secret
        doc.append("### Kubernetes Secret\n\n");
        doc.append("```yaml\napiVersion: v1\nkind: Secret\nmetadata:\n  name: ")
           .append(result.getProjectName().toLowerCase().replace(" ", "-"))
           .append("-secrets\nstringData:\n");
        for (EnvVariable var : sortedVariables) {
            if (var.isRequired()) {
                doc.append("  ").append(var.getName()).append(": ");
                if (var.getExampleValue() != null) {
                    doc.append("\"").append(var.getExampleValue()).append("\"");
                } else {
                    doc.append("\"<value>\"");
                }
                doc.append("\n");
            }
        }
        doc.append("```\n\n");

        // .env файл
        doc.append("### .env (локальная разработка)\n\n");
        doc.append("```env\n# ").append(result.getProjectName()).append(" environment variables\n\n");

        // Группируем по категориям в .env
        for (Map.Entry<String, List<EnvVariable>> entry : categorized.entrySet()) {
            doc.append("# ").append(entry.getKey()).append("\n");
            for (EnvVariable var : entry.getValue()) {
                doc.append(var.getName()).append("=");
                if (var.getDefaultValue() != null) {
                    doc.append(var.getDefaultValue());
                } else if (var.getExampleValue() != null) {
                    doc.append(var.getExampleValue());
                }
                doc.append("\n");
            }
            doc.append("\n");
        }
        doc.append("```\n");

        return doc.toString();
    }

    /**
     * Группирует переменные по категориям.
     */
    private Map<String, List<EnvVariable>> categorizeVariables(List<EnvVariable> variables) {
        Map<String, List<EnvVariable>> categorized = new LinkedHashMap<>();

        for (EnvVariable var : variables) {
            String category = var.getCategory() != null ? var.getCategory() : detectCategory(var);
            categorized.computeIfAbsent(category, k -> new ArrayList<>()).add(var);
        }

        return categorized;
    }

    private List<EnvVariable> sortVariables(List<EnvVariable> variables) {
        return variables.stream()
                .sorted(Comparator
                        .comparing((EnvVariable v) -> v.getCategory() != null ? v.getCategory() : detectCategory(v))
                        .thenComparing(EnvVariable::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    private String formatDefaultValueCell(String value) {
        if (value == null) {
            return "-";
        }
        return "`" + escapeMarkdownTableCell(value) + "`";
    }

    private String escapeMarkdownTableCell(String value) {
        if (value == null) {
            return "-";
        }
        return value.replace("|", "\\|")
                    .replace("`", "\\`")
                    .replace("\n", "<br>");
    }

    /**
     * Определяет категорию переменной по её имени и использованию.
     */
    private String detectCategory(EnvVariable var) {
        String name = var.getName().toUpperCase();

        if (name.contains("DATABASE") || name.contains("DB_") || name.contains("_DB") ||
            name.contains("POSTGRES") || name.contains("MYSQL") || name.contains("MONGO") ||
            name.contains("JDBC") || name.contains("DATASOURCE")) {
            return "База данных";
        }

        if (name.contains("AUTH") || name.contains("TOKEN") || name.contains("SECRET") ||
            name.contains("PASSWORD") || name.contains("API_KEY") || name.contains("CREDENTIAL") ||
            name.contains("JWT") || name.contains("OAUTH")) {
            return "Безопасность";
        }

        if (name.contains("KAFKA") || name.contains("RABBIT") || name.contains("MQ") ||
            name.contains("QUEUE") || name.contains("AMQP")) {
            return "Очереди сообщений";
        }

        if (name.contains("REDIS") || name.contains("CACHE") || name.contains("MEMCACHED")) {
            return "Кэширование";
        }

        if (name.contains("LOG") || name.contains("DEBUG") || name.contains("TRACE")) {
            return "Логирование";
        }

        if (name.contains("SERVER") || name.contains("PORT") || name.contains("HOST") ||
            name.contains("URL") || name.contains("ENDPOINT")) {
            return "Сервер и сеть";
        }

        if (name.contains("FEATURE") || name.contains("FLAG") || name.contains("ENABLED") ||
            name.contains("TOGGLE")) {
            return "Feature Flags";
        }

        // Анализ использований
        if (var.getUsages() != null && !var.getUsages().isEmpty()) {
            for (VariableUsage usage : var.getUsages()) {
                if (usage.getPurpose() != null) {
                    switch (usage.getPurpose()) {
                        case DATABASE_CONNECTION:
                            return "База данных";
                        case AUTHENTICATION:
                            return "Безопасность";
                        case EXTERNAL_API:
                            return "Внешние сервисы";
                        case LOGGING_CONFIG:
                            return "Логирование";
                        case CACHE_CONFIG:
                            return "Кэширование";
                        case MESSAGING_CONFIG:
                            return "Очереди сообщений";
                        case SERVER_CONFIG:
                            return "Сервер и сеть";
                        case FEATURE_FLAG:
                            return "Feature Flags";
                        default:
                            break;
                    }
                }
            }
        }

        return "Прочее";
    }
}
