package com.example.envdoc.tools;

import dev.langchain4j.agent.tool.Tool;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Tool для GigaChat - получение полного исходного кода Java класса.
 */
@Slf4j
@Component
public class ClassCodeTool {

    @Setter
    private Path repoPath;

    /**
     * Получает полный исходный код Java класса по его имени.
     *
     * @param className полное имя класса (например, com.example.MyClass)
     * @return исходный код класса или сообщение об ошибке
     */
    @Tool("Получить полный исходный код Java класса по имени. " +
          "Используйте полное имя класса, например: com.example.service.MyService")
    public String getClassCode(String className) {
        if (repoPath == null) {
            return "Error: Repository path not set";
        }

        log.info("Getting code for class: {}", className);

        // Преобразуем имя класса в путь к файлу
        String relativePath = className.replace('.', '/') + ".java";

        try {
            // Ищем файл в стандартных директориях
            Path[] searchDirs = {
                    repoPath.resolve("src/main/java"),
                    repoPath.resolve("src"),
                    repoPath
            };

            for (Path searchDir : searchDirs) {
                Path filePath = searchDir.resolve(relativePath);
                if (Files.exists(filePath)) {
                    String content = Files.readString(filePath);
                    log.debug("Found class file: {}", filePath);
                    return limitToMaxLines(content, 1000);
                }
            }

            // Если не найден по точному пути, ищем по имени файла
            String simpleClassName = className.contains(".")
                    ? className.substring(className.lastIndexOf('.') + 1)
                    : className;
            String fileName = simpleClassName + ".java";

            try (Stream<Path> paths = Files.walk(repoPath)) {
                Path found = paths
                        .filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().equals(fileName))
                        .filter(p -> !p.toString().contains("/test/"))
                        .filter(p -> !p.toString().contains("/target/"))
                        .findFirst()
                        .orElse(null);

                if (found != null) {
                    String content = Files.readString(found);
                    log.debug("Found class file by name: {}", found);
                    return limitToMaxLines(content, 1000);
                }
            }

            log.warn("Class not found: {}", className);
            return "Class not found: " + className;

        } catch (IOException e) {
            log.error("Error reading class file: {}", className, e);
            return "Error reading class: " + e.getMessage();
        }
    }

    /**
     * Получает список всех классов в пакете.
     *
     * @param packageName имя пакета
     * @return список классов в пакете
     */
    @Tool("Получить список всех классов в указанном пакете")
    public String listClassesInPackage(String packageName) {
        if (repoPath == null) {
            return "Error: Repository path not set";
        }

        String packagePath = packageName.replace('.', '/');

        try {
            Path[] searchDirs = {
                    repoPath.resolve("src/main/java").resolve(packagePath),
                    repoPath.resolve("src").resolve(packagePath)
            };

            StringBuilder result = new StringBuilder();
            result.append("Classes in package ").append(packageName).append(":\n");

            for (Path searchDir : searchDirs) {
                if (Files.exists(searchDir) && Files.isDirectory(searchDir)) {
                    try (Stream<Path> paths = Files.list(searchDir)) {
                        paths.filter(Files::isRegularFile)
                             .filter(p -> p.toString().endsWith(".java"))
                             .forEach(p -> {
                                 String fileName = p.getFileName().toString();
                                 String className = fileName.replace(".java", "");
                                 result.append("- ").append(packageName).append(".").append(className).append("\n");
                             });
                    }
                    break;
                }
            }

            return result.toString();

        } catch (IOException e) {
            log.error("Error listing classes in package: {}", packageName, e);
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Ищет классы по паттерну в имени.
     *
     * @param pattern паттерн для поиска (часть имени класса)
     * @return список найденных классов
     */
    @Tool("Найти классы по паттерну в имени. Например: 'Config' найдёт все конфигурационные классы")
    public String findClassesByPattern(String pattern) {
        if (repoPath == null) {
            return "Error: Repository path not set";
        }

        try {
            StringBuilder result = new StringBuilder();
            result.append("Classes matching '").append(pattern).append("':\n");

            try (Stream<Path> paths = Files.walk(repoPath)) {
                paths.filter(Files::isRegularFile)
                     .filter(p -> p.toString().endsWith(".java"))
                     .filter(p -> !p.toString().contains("/test/"))
                     .filter(p -> !p.toString().contains("/target/"))
                     .filter(p -> p.getFileName().toString().toLowerCase()
                             .contains(pattern.toLowerCase()))
                     .limit(20) // Ограничиваем количество результатов
                     .forEach(p -> {
                         String relativePath = repoPath.relativize(p).toString();
                         result.append("- ").append(relativePath).append("\n");
                     });
            }

            return result.toString();

        } catch (IOException e) {
            log.error("Error finding classes by pattern: {}", pattern, e);
            return "Error: " + e.getMessage();
        }
    }

    private String limitToMaxLines(String content, int maxLines) {
        if (content == null || maxLines <= 0) {
            return content;
        }
        String[] lines = content.split("\\R", -1);
        if (lines.length <= maxLines) {
            return content;
        }
        StringBuilder truncated = new StringBuilder();
        for (int i = 0; i < maxLines; i++) {
            truncated.append(lines[i]).append("\n");
        }
        truncated.append("...truncated...\n");
        return truncated.toString();
    }
}
