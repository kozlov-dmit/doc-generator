package com.example.envdoc.service.documentation;

import com.example.envdoc.model.EnvVariable;
import com.example.envdoc.model.VariableUsage;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Строит системное сообщение и промпт для GigaChat.
 */
@Component
public class GigaChatPromptBuilder {

    public String systemMessage() {
        return "Ты — технический писатель, создающий документацию для DevOps команды. " +
               "Отвечай на русском языке. Формат ответа — Markdown. " +
               "Вывод должен быть строго в одном формате и содержать одну таблицу со всеми параметрами. " +
               "Если нужна дополнительная информация о коде классов, используй доступные инструменты.";
    }

    public String buildPrompt(List<EnvVariable> variables, String projectName) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("ЗАДАЧА: Создать документацию по переменным окружения для проекта \"")
              .append(projectName)
              .append("\".\n\n");

        prompt.append("ВХОДНЫЕ ДАННЫЕ В JSON:\n");
        prompt.append("```json\n");
        prompt.append(buildInputJson(variables, projectName));
        prompt.append("\n```\n\n");

        prompt.append("""
                ТРЕБОВАНИЯ К ДОКУМЕНТАЦИИ:
                1. Вывод только в Markdown без дополнительных пояснений вне Markdown.
                2. Структура строго фиксирована:
                   - Заголовок: # Переменные окружения для проекта {projectName}
                   - Одна таблица со всеми параметрами, без дополнительных разделов
                3. В таблице должны быть все данные по параметрам, одна строка на параметр.
                4. Колонки таблицы (строго в этом порядке):
                   Переменная | Описание | Тип | Обязательная | По умолчанию | Пример | Категория | Модуль | Источник | Инициализация | Использования
                5. Источник — это путь к файлу с номером строки, если есть.
                6. Инициализация — кратко, где и как задано (например: application.yml, @Value, System.getenv).
                7. Использования — краткий список классов/методов (одно поле).
                8. Если данных нет, ставь "-".
                9. Никаких дополнительных секций, списков, кода или примеров.
                """);

        return prompt.toString();
    }

    private String buildInputJson(List<EnvVariable> variables, String projectName) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"projectName\":\"").append(escapeJson(projectName)).append("\",");
        json.append("\"variables\":[");
        for (int i = 0; i < variables.size(); i++) {
            EnvVariable var = variables.get(i);
            json.append("{");
            json.append("\"name\":\"").append(escapeJson(var.getName())).append("\",");
            json.append("\"required\":").append(var.isRequired()).append(",");
            json.append("\"defaultValue\":").append(toJsonValue(var.getDefaultValue())).append(",");
            json.append("\"description\":").append(toJsonValue(var.getDescription())).append(",");
            json.append("\"type\":").append(toJsonValue(var.getDataType())).append(",");
            json.append("\"example\":").append(toJsonValue(var.getExampleValue())).append(",");
            json.append("\"category\":").append(toJsonValue(var.getCategory())).append(",");
            json.append("\"definition\":");
            if (var.getDefinition() == null) {
                json.append("null,");
            } else {
                json.append("{");
                json.append("\"type\":\"").append(escapeJson(String.valueOf(var.getDefinition().getType()))).append("\",");
                json.append("\"filePath\":").append(toJsonValue(var.getDefinition().getFilePath())).append(",");
                json.append("\"lineNumber\":").append(var.getDefinition().getLineNumber()).append(",");
                json.append("\"moduleName\":").append(toJsonValue(var.getDefinition().getModuleName())).append(",");
                json.append("\"codeSnippet\":").append(toJsonValue(var.getDefinition().getCodeSnippet()));
                json.append("},");
            }
            json.append("\"usages\":[");
            if (var.getUsages() != null) {
                for (int u = 0; u < var.getUsages().size(); u++) {
                    VariableUsage usage = var.getUsages().get(u);
                    json.append("{");
                    json.append("\"className\":").append(toJsonValue(usage.getClassName())).append(",");
                    json.append("\"methodName\":").append(toJsonValue(usage.getMethodName())).append(",");
                    json.append("\"lineNumber\":").append(usage.getLineNumber()).append(",");
                    json.append("\"purpose\":").append(toJsonValue(String.valueOf(usage.getPurpose()))).append(",");
                    json.append("\"context\":").append(toJsonValue(usage.getUsageContext()));
                    json.append("}");
                    if (u < var.getUsages().size() - 1) {
                        json.append(",");
                    }
                }
            }
            json.append("]");
            json.append("}");
            if (i < variables.size() - 1) {
                json.append(",");
            }
        }
        json.append("]}");
        return json.toString();
    }

    private String toJsonValue(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + escapeJson(value) + "\"";
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }
}
