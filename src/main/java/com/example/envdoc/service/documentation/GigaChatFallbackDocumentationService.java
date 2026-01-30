package com.example.envdoc.service.documentation;

import com.example.envdoc.model.EnvVariable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Генерация документации без GigaChat.
 */
@Service
public class GigaChatFallbackDocumentationService {

    public String generateDocumentation(List<EnvVariable> variables, String projectName) {
        StringBuilder doc = new StringBuilder();

        doc.append("# Переменные окружения для проекта ").append(projectName).append("\n\n");

        doc.append("| Переменная | Описание | Тип | Обязательная | По умолчанию | Пример | Категория | Модуль | Источник | Инициализация | Использования |\n");
        doc.append("|------------|----------|-----|--------------|-------------|--------|-----------|--------|----------|---------------|--------------|\n");

        for (EnvVariable var : variables) {
            doc.append("| `").append(var.getName()).append("` | ");
            doc.append(var.getDescription() != null ? var.getDescription() : "-").append(" | ");
            doc.append(var.getDataType() != null ? var.getDataType() : "string").append(" | ");
            doc.append(var.isRequired() ? "Да" : "Нет").append(" | ");
            doc.append(var.getDefaultValue() != null ? "`" + var.getDefaultValue() + "`" : "-").append(" | ");
            doc.append(var.getExampleValue() != null ? "`" + var.getExampleValue() + "`" : "-").append(" | ");
            doc.append(var.getCategory() != null ? var.getCategory() : "-").append(" | ");
            doc.append(var.getDefinition() != null && var.getDefinition().getModuleName() != null
                       ? "`" + var.getDefinition().getModuleName() + "`"
                       : "-").append(" | ");
            if (var.getDefinition() != null && var.getDefinition().getFilePath() != null) {
                doc.append("`").append(var.getDefinition().getFilePath());
                if (var.getDefinition().getLineNumber() > 0) {
                    doc.append(":").append(var.getDefinition().getLineNumber());
                }
                doc.append("` | ");
            } else {
                doc.append("- | ");
            }
            doc.append(var.getDefinition() != null ? var.getDefinition().getType() : "-").append(" | ");
            if (var.getUsages() != null && !var.getUsages().isEmpty()) {
                String usages = var.getUsages().stream()
                        .map(u -> u.getClassName() + "." + u.getMethodName() + "()")
                        .distinct()
                        .reduce((a, b) -> a + "; " + b)
                        .orElse("-");
                doc.append(escapeTableCell(usages));
            } else {
                doc.append("-");
            }
            doc.append(" |\n");
        }

        return doc.toString();
    }

    private String escapeTableCell(String value) {
        if (value == null) {
            return "-";
        }
        return value.replace("|", "\\|").replace("\n", " ");
    }
}
