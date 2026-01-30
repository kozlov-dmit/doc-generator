package com.example.envdoc.service;

import com.example.envdoc.model.DefinitionType;
import com.example.envdoc.model.EnvVariable;
import com.example.envdoc.model.VariableDefinition;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Сервис для извлечения переменных окружения из исходного кода.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnvVarExtractor {

    private final SourceCodeAnalyzer sourceCodeAnalyzer;

    // Паттерны для поиска переменных
    // Поддерживаем как env-стиль (UPPER_SNAKE), так и property-стиль (db.url, my-key)
    private static final Pattern ENV_VAR_PATTERN =
            Pattern.compile("\\$\\{([A-Za-z0-9_.-]+)(:[^}]*)?}");

    /**
     * Извлекает все переменные окружения из репозитория.
     *
     * @param repoPath путь к репозиторию
     * @return Map с переменными окружения (ключ - имя переменной)
     */
    public Map<String, EnvVariable> extractAllVariables(Path repoPath) {
        Map<String, EnvVariable> variables = new LinkedHashMap<>();
        Map<String, String> propertyDefaults = collectPropertyDefaults(repoPath);

        // 1. Анализ YAML/Properties файлов
        log.info("Analyzing configuration files...");
        extractFromConfigFiles(repoPath, variables);

        // 2. Анализ Java файлов
        log.info("Analyzing Java files...");
        extractFromJavaFiles(repoPath, variables, propertyDefaults);

        log.info("Found {} environment variables", variables.size());
        return variables;
    }

    /**
     * Извлекает переменные из конфигурационных файлов (YAML, Properties).
     */
    private void extractFromConfigFiles(Path repoPath, Map<String, EnvVariable> variables) {
        List<Path> configFiles = sourceCodeAnalyzer.findConfigFiles(repoPath);

        for (Path configFile : configFiles) {
            String relativePath = repoPath.relativize(configFile).toString();
            String moduleName = resolveModuleName(repoPath, configFile);
            log.debug("Processing config file: {}", relativePath);

            if (configFile.toString().endsWith(".yml") || configFile.toString().endsWith(".yaml")) {
                extractFromYaml(configFile, relativePath, moduleName, variables);
            } else if (configFile.toString().endsWith(".properties")) {
                extractFromProperties(configFile, relativePath, moduleName, variables);
            }
        }
    }

    /**
     * Извлекает переменные из YAML файла.
     */
    private void extractFromYaml(Path yamlFile,
                                 String relativePath,
                                 String moduleName,
                                 Map<String, EnvVariable> variables) {
        try {
            String content = Files.readString(yamlFile);
            List<String> lines = Files.readAllLines(yamlFile);

            // Поиск паттернов ${VAR} в содержимом
            Matcher matcher = ENV_VAR_PATTERN.matcher(content);
            while (matcher.find()) {
                String varName = matcher.group(1);
                String defaultValue = matcher.group(2);

                if (defaultValue != null && defaultValue.startsWith(":")) {
                    defaultValue = defaultValue.substring(1);
                }

                // Найти строку, где определена переменная
                int lineNumber = findLineNumber(lines, matcher.group(0));

                // Получить фрагмент кода
                String codeSnippet = getYamlSnippet(lines, lineNumber);

                VariableDefinition definition = VariableDefinition.builder()
                        .type(DefinitionType.APPLICATION_YAML)
                        .filePath(relativePath)
                        .lineNumber(lineNumber)
                        .codeSnippet(codeSnippet)
                        .moduleName(moduleName)
                        .build();

                EnvVariable existing = variables.get(varName);
                if (existing == null) {
                    EnvVariable envVar = EnvVariable.builder()
                            .name(varName)
                            .defaultValue(defaultValue)
                            .required(defaultValue == null || defaultValue.isBlank())
                            .definition(definition)
                            .usages(new ArrayList<>())
                            .build();
                    variables.put(varName, envVar);
                }
            }
        } catch (IOException e) {
            log.error("Error reading YAML file: {}", yamlFile, e);
        }
    }

    /**
     * Извлекает переменные из Properties файла.
     */
    private void extractFromProperties(Path propsFile,
                                       String relativePath,
                                       String moduleName,
                                       Map<String, EnvVariable> variables) {
        try {
            List<String> lines = Files.readAllLines(propsFile);

            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                Matcher matcher = ENV_VAR_PATTERN.matcher(line);

                while (matcher.find()) {
                    String varName = matcher.group(1);
                    String defaultValue = matcher.group(2);

                    if (defaultValue != null && defaultValue.startsWith(":")) {
                        defaultValue = defaultValue.substring(1);
                    }

                    VariableDefinition definition = VariableDefinition.builder()
                            .type(DefinitionType.APPLICATION_PROPERTIES)
                            .filePath(relativePath)
                            .lineNumber(i + 1)
                            .codeSnippet(line.trim())
                            .moduleName(moduleName)
                            .build();

                    if (!variables.containsKey(varName)) {
                        EnvVariable envVar = EnvVariable.builder()
                                .name(varName)
                                .defaultValue(defaultValue)
                                .required(defaultValue == null || defaultValue.isBlank())
                                .definition(definition)
                                .usages(new ArrayList<>())
                                .build();
                        variables.put(varName, envVar);
                    }
                }
            }
        } catch (IOException e) {
            log.error("Error reading properties file: {}", propsFile, e);
        }
    }

    /**
     * Извлекает переменные из Java файлов.
     */
    private void extractFromJavaFiles(Path repoPath,
                                      Map<String, EnvVariable> variables,
                                      Map<String, String> propertyDefaults) {
        List<Path> javaFiles = sourceCodeAnalyzer.findJavaFiles(repoPath);

        for (Path javaFile : javaFiles) {
            String relativePath = repoPath.relativize(javaFile).toString();
            String moduleName = resolveModuleName(repoPath, javaFile);
            log.debug("Processing Java file: {}", relativePath);

            sourceCodeAnalyzer.parseJavaFile(javaFile).ifPresent(cu -> {
                String className = sourceCodeAnalyzer.extractFullClassName(cu);

                // 1. @Value аннотации
                extractFromValueAnnotations(cu, relativePath, className, moduleName, variables);

                // 2. @ConfigurationProperties
                extractFromConfigProperties(cu, relativePath, className, moduleName, variables, propertyDefaults);

                // 3. System.getenv()
                extractFromSystemGetenv(cu, relativePath, className, moduleName, variables);

                // 4. System.getProperty()
                extractFromSystemGetProperty(cu, relativePath, className, moduleName, variables);

                // 5. Environment.getProperty()
                extractFromEnvironmentApi(cu, relativePath, className, moduleName, variables);
            });
        }
    }

    /**
     * Извлекает переменные из аннотаций @Value.
     */
    private void extractFromValueAnnotations(CompilationUnit cu,
                                             String filePath,
                                             String className,
                                             String moduleName,
                                             Map<String, EnvVariable> variables) {
        List<FieldDeclaration> fields = sourceCodeAnalyzer.findFieldsWithAnnotation(cu, "Value");

        for (FieldDeclaration field : fields) {
            Optional<AnnotationExpr> valueAnnotation = field.getAnnotationByName("Value");
            if (valueAnnotation.isEmpty()) continue;

            Optional<String> annotationContent = sourceCodeAnalyzer.extractValueAnnotationContent(valueAnnotation.get());
            if (annotationContent.isEmpty()) continue;

            String content = annotationContent.get();
            Matcher matcher = ENV_VAR_PATTERN.matcher(content);

            while (matcher.find()) {
                String varName = matcher.group(1);
                String defaultValue = matcher.group(2);

                if (defaultValue != null && defaultValue.startsWith(":")) {
                    defaultValue = defaultValue.substring(1);
                }

                String fieldName = field.getVariables().stream()
                        .findFirst()
                        .map(VariableDeclarator::getNameAsString)
                        .orElse("unknown");

                int lineNumber = field.getBegin().map(p -> p.line).orElse(0);

                VariableDefinition definition = VariableDefinition.builder()
                        .type(DefinitionType.SPRING_VALUE)
                        .filePath(filePath)
                        .lineNumber(lineNumber)
                        .className(className)
                        .fieldOrMethodName(fieldName)
                        .codeSnippet(field.toString())
                        .moduleName(moduleName)
                        .build();

                if (!variables.containsKey(varName)) {
                    EnvVariable envVar = EnvVariable.builder()
                            .name(varName)
                            .defaultValue(defaultValue)
                            .required(defaultValue == null || defaultValue.isBlank())
                            .definition(definition)
                            .usages(new ArrayList<>())
                            .build();
                    variables.put(varName, envVar);
                } else {
                    // Обновляем definition если уже есть из YAML
                    EnvVariable existing = variables.get(varName);
                    if (existing.getDefinition().getType() == DefinitionType.APPLICATION_YAML) {
                        // YAML приоритетнее, но сохраняем информацию о @Value использовании
                    }
                }
            }
        }
    }

    /**
     * Извлекает переменные из классов с @ConfigurationProperties.
     */
    private void extractFromConfigProperties(CompilationUnit cu,
                                             String filePath,
                                             String className,
                                             String moduleName,
                                             Map<String, EnvVariable> variables,
                                             Map<String, String> propertyDefaults) {
        if (!sourceCodeAnalyzer.hasClassAnnotation(cu, "ConfigurationProperties")) {
            return;
        }

        // Находим prefix аннотации
        cu.findAll(AnnotationExpr.class).stream()
                .filter(a -> a.getNameAsString().equals("ConfigurationProperties"))
                .findFirst()
                .ifPresent(annotation -> {
                    String prefix = extractConfigPropertiesPrefix(annotation);
                    if (prefix != null) {
                        // Анализируем поля класса
                        cu.findAll(FieldDeclaration.class).forEach(field -> {
                            field.getVariables().forEach(var -> {
                                String propertyName = prefix + "." + camelToKebab(var.getNameAsString());
                                String envVarName = propertyName.toUpperCase().replace(".", "_").replace("-", "_");

                                int lineNumber = field.getBegin().map(p -> p.line).orElse(0);
                                String defaultValue = resolvePropertyDefault(propertyDefaults, propertyName);
                                if (defaultValue == null) {
                                    defaultValue = var.getInitializer()
                                            .map(this::extractLiteralValue)
                                            .orElse(null);
                                }

                                if (!variables.containsKey(envVarName)) {
                                    VariableDefinition definition = VariableDefinition.builder()
                                            .type(DefinitionType.CONFIG_PROPERTIES)
                                            .filePath(filePath)
                                            .lineNumber(lineNumber)
                                            .className(className)
                                            .fieldOrMethodName(var.getNameAsString())
                                            .codeSnippet(field.toString())
                                            .moduleName(moduleName)
                                            .build();

                                    EnvVariable envVar = EnvVariable.builder()
                                            .name(envVarName)
                                            .defaultValue(defaultValue)
                                            .required(defaultValue == null)
                                            .definition(definition)
                                            .usages(new ArrayList<>())
                                            .build();
                                    variables.put(envVarName, envVar);
                                }
                            });
                        });
                    }
                });
    }

    /**
     * Извлекает переменные из вызовов System.getenv().
     */
    private void extractFromSystemGetenv(CompilationUnit cu,
                                         String filePath,
                                         String className,
                                         String moduleName,
                                         Map<String, EnvVariable> variables) {
        List<MethodCallExpr> getenvCalls = sourceCodeAnalyzer.findMethodCalls(cu, "getenv");

        for (MethodCallExpr call : getenvCalls) {
            if (call.getScope().map(s -> s.toString().equals("System")).orElse(false)) {
                call.getArguments().stream()
                        .filter(arg -> arg instanceof StringLiteralExpr)
                        .map(arg -> ((StringLiteralExpr) arg).getValue())
                        .forEach(varName -> {
                            int lineNumber = call.getBegin().map(p -> p.line).orElse(0);

                            // Найти метод, содержащий этот вызов
                            String methodName = findContainingMethodName(cu, lineNumber);

                            if (!variables.containsKey(varName)) {
                                VariableDefinition definition = VariableDefinition.builder()
                                        .type(DefinitionType.SYSTEM_GETENV)
                                        .filePath(filePath)
                                        .lineNumber(lineNumber)
                                        .className(className)
                                        .fieldOrMethodName(methodName)
                                        .codeSnippet(call.toString())
                                        .moduleName(moduleName)
                                        .build();

                                EnvVariable envVar = EnvVariable.builder()
                                        .name(varName)
                                        .required(true) // System.getenv обычно для обязательных переменных
                                        .definition(definition)
                                        .usages(new ArrayList<>())
                                        .build();
                                variables.put(varName, envVar);
                            }
                        });
            }
        }
    }

    /**
     * Извлекает переменные из вызовов System.getProperty().
     */
    private void extractFromSystemGetProperty(CompilationUnit cu,
                                              String filePath,
                                              String className,
                                              String moduleName,
                                              Map<String, EnvVariable> variables) {
        List<MethodCallExpr> getPropertyCalls = sourceCodeAnalyzer.findMethodCalls(cu, "getProperty");

        for (MethodCallExpr call : getPropertyCalls) {
            if (call.getScope().map(s -> s.toString().equals("System")).orElse(false)) {
                if (!call.getArguments().isEmpty() && call.getArgument(0) instanceof StringLiteralExpr) {
                    String propName = ((StringLiteralExpr) call.getArgument(0)).getValue();

                    // Преобразуем в формат переменной окружения
                    String varName = propName.toUpperCase().replace(".", "_").replace("-", "_");

                    int lineNumber = call.getBegin().map(p -> p.line).orElse(0);
                    String methodName = findContainingMethodName(cu, lineNumber);

                    // Проверяем наличие default value
                    String defaultValue = null;
                    if (call.getArguments().size() > 1) {
                        defaultValue = extractLiteralValue(call.getArgument(1));
                    }

                    if (!variables.containsKey(varName)) {
                        VariableDefinition definition = VariableDefinition.builder()
                                .type(DefinitionType.SYSTEM_PROPERTY)
                                .filePath(filePath)
                                .lineNumber(lineNumber)
                                .className(className)
                                .fieldOrMethodName(methodName)
                                .codeSnippet(call.toString())
                                .moduleName(moduleName)
                                .build();

                        EnvVariable envVar = EnvVariable.builder()
                                .name(varName)
                                .defaultValue(defaultValue)
                                .required(defaultValue == null)
                                .definition(definition)
                                .usages(new ArrayList<>())
                                .build();
                        variables.put(varName, envVar);
                    }
                }
            }
        }
    }

    /**
     * Извлекает переменные из Spring Environment API.
     */
    private void extractFromEnvironmentApi(CompilationUnit cu, String filePath, String className,
                                           String moduleName,
                                           Map<String, EnvVariable> variables) {
        List<MethodCallExpr> getPropertyCalls = sourceCodeAnalyzer.findMethodCalls(cu, "getProperty");

        for (MethodCallExpr call : getPropertyCalls) {
            // Проверяем, что это вызов на Environment объекте
            Optional<String> scope = call.getScope().map(Object::toString);
            if (scope.isPresent() && (scope.get().contains("environment") ||
                                       scope.get().contains("Environment") ||
                                       scope.get().contains("env"))) {

                if (!call.getArguments().isEmpty() && call.getArgument(0) instanceof StringLiteralExpr) {
                    String propName = ((StringLiteralExpr) call.getArgument(0)).getValue();

                    // Проверяем, содержит ли имя placeholder ${...}
                    Matcher matcher = ENV_VAR_PATTERN.matcher(propName);
                    String varName;
                    if (matcher.find()) {
                        varName = matcher.group(1);
                    } else {
                        varName = propName.toUpperCase().replace(".", "_").replace("-", "_");
                    }

                    int lineNumber = call.getBegin().map(p -> p.line).orElse(0);
                    String methodName = findContainingMethodName(cu, lineNumber);

                    String defaultValue = null;
                    if (call.getArguments().size() > 1) {
                        defaultValue = extractLiteralValue(call.getArgument(1));
                    }

                    if (!variables.containsKey(varName)) {
                        VariableDefinition definition = VariableDefinition.builder()
                                .type(DefinitionType.ENVIRONMENT_API)
                                .filePath(filePath)
                                .lineNumber(lineNumber)
                                .className(className)
                                .fieldOrMethodName(methodName)
                                .codeSnippet(call.toString())
                                .moduleName(moduleName)
                                .build();

                        EnvVariable envVar = EnvVariable.builder()
                                .name(varName)
                                .defaultValue(defaultValue)
                                .required(defaultValue == null)
                                .definition(definition)
                                .usages(new ArrayList<>())
                                .build();
                        variables.put(varName, envVar);
                    }
                }
            }
        }
    }

    // ===== Вспомогательные методы =====

    private int findLineNumber(List<String> lines, String searchText) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(searchText)) {
                return i + 1;
            }
        }
        return 1;
    }

    private String getYamlSnippet(List<String> lines, int lineNumber) {
        int start = Math.max(0, lineNumber - 3);
        int end = Math.min(lines.size(), lineNumber + 1);

        StringBuilder snippet = new StringBuilder();
        for (int i = start; i < end; i++) {
            snippet.append(lines.get(i)).append("\n");
        }
        return snippet.toString().trim();
    }

    private String extractConfigPropertiesPrefix(AnnotationExpr annotation) {
        if (annotation.isSingleMemberAnnotationExpr()) {
            return annotation.asSingleMemberAnnotationExpr()
                    .getMemberValue().toString()
                    .replace("\"", "");
        } else if (annotation.isNormalAnnotationExpr()) {
            return annotation.asNormalAnnotationExpr().getPairs().stream()
                    .filter(p -> p.getNameAsString().equals("prefix") || p.getNameAsString().equals("value"))
                    .findFirst()
                    .map(p -> p.getValue().toString().replace("\"", ""))
                    .orElse(null);
        }
        return null;
    }

    private String camelToKebab(String camelCase) {
        return camelCase.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
    }

    private String findContainingMethodName(CompilationUnit cu, int lineNumber) {
        return cu.findAll(com.github.javaparser.ast.body.MethodDeclaration.class).stream()
                .filter(md -> md.getBegin().isPresent() && md.getEnd().isPresent())
                .filter(md -> md.getBegin().get().line <= lineNumber && md.getEnd().get().line >= lineNumber)
                .findFirst()
                .map(md -> md.getNameAsString())
                .orElse("unknown");
    }

    private String extractLiteralValue(Expression expr) {
        if (expr.isStringLiteralExpr()) {
            return expr.asStringLiteralExpr().getValue();
        }
        if (expr.isBooleanLiteralExpr()) {
            return Boolean.toString(expr.asBooleanLiteralExpr().getValue());
        }
        if (expr.isIntegerLiteralExpr()) {
            return expr.asIntegerLiteralExpr().getValue();
        }
        if (expr.isLongLiteralExpr()) {
            return expr.asLongLiteralExpr().getValue();
        }
        if (expr.isDoubleLiteralExpr()) {
            return expr.asDoubleLiteralExpr().getValue();
        }
        if (expr.isCharLiteralExpr()) {
            return String.valueOf(expr.asCharLiteralExpr().getValue());
        }
        return null;
    }

    private Map<String, String> collectPropertyDefaults(Path repoPath) {
        Map<String, DefaultValue> collected = new HashMap<>();

        List<Path> configFiles = sourceCodeAnalyzer.findConfigFiles(repoPath);
        for (Path configFile : configFiles) {
            String filename = configFile.getFileName().toString().toLowerCase();
            int priority = filePriority(filename);

            if (filename.endsWith(".properties")) {
                Map<String, String> props = readProperties(configFile);
                mergeDefaults(collected, props, priority);
            } else if (filename.endsWith(".yml") || filename.endsWith(".yaml")) {
                Map<String, String> yamlProps = readYamlAsProperties(configFile);
                mergeDefaults(collected, yamlProps, priority);
            }
        }

        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, DefaultValue> entry : collected.entrySet()) {
            result.put(entry.getKey(), entry.getValue().value);
        }
        return result;
    }

    private Map<String, String> readProperties(Path file) {
        Properties properties = new Properties();
        try (var in = Files.newInputStream(file)) {
            properties.load(in);
        } catch (IOException e) {
            log.debug("Failed to read properties: {}", file, e);
            return Map.of();
        }

        Map<String, String> result = new HashMap<>();
        for (String name : properties.stringPropertyNames()) {
            String value = properties.getProperty(name);
            String resolved = resolvePlaceholderDefault(value);
            if (resolved != null) {
                result.put(normalizePropertyKey(name), resolved);
            }
        }
        return result;
    }

    private Map<String, String> readYamlAsProperties(Path file) {
        try {
            String content = Files.readString(file);
            Yaml yaml = new Yaml();
            Object data = yaml.load(content);
            Map<String, String> result = new HashMap<>();
            if (data instanceof Map<?, ?> map) {
                flattenYamlMap("", map, result);
            }
            return result;
        } catch (Exception e) {
            log.debug("Failed to read yaml: {}", file, e);
            return Map.of();
        }
    }

    private void flattenYamlMap(String prefix, Map<?, ?> map, Map<String, String> result) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                flattenYamlMap(fullKey, nested, result);
            } else if (value != null) {
                String strValue = String.valueOf(value);
                String resolved = resolvePlaceholderDefault(strValue);
                if (resolved != null) {
                    result.put(normalizePropertyKey(fullKey), resolved);
                }
            }
        }
    }

    private void mergeDefaults(Map<String, DefaultValue> collected,
                               Map<String, String> values,
                               int priority) {
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = normalizePropertyKey(entry.getKey());
            String value = entry.getValue();
            DefaultValue existing = collected.get(key);
            if (existing == null || priority > existing.priority) {
                collected.put(key, new DefaultValue(value, priority));
            }
        }
    }

    private String resolvePropertyDefault(Map<String, String> defaults, String propertyName) {
        for (String candidate : relaxedPropertyKeys(propertyName)) {
            String value = defaults.get(normalizePropertyKey(candidate));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Set<String> relaxedPropertyKeys(String propertyName) {
        Set<String> keys = new LinkedHashSet<>();
        keys.add(propertyName);
        keys.add(propertyName.replace('-', '_'));
        keys.add(propertyName.replace('.', '_'));
        keys.add(propertyName.replace('.', '-'));
        keys.add(propertyName.replace('.', '_').replace('-', '_'));
        return keys;
    }

    private String normalizePropertyKey(String key) {
        return key == null ? "" : key.trim().toLowerCase();
    }

    private String resolvePlaceholderDefault(String value) {
        if (value == null) {
            return null;
        }
        Matcher matcher = ENV_VAR_PATTERN.matcher(value);
        if (!matcher.find()) {
            return value;
        }

        String defaultValue = matcher.group(2);
        if (defaultValue != null && defaultValue.startsWith(":")) {
            return defaultValue.substring(1);
        }
        return null;
    }

    private String resolveModuleName(Path repoPath, Path filePath) {
        Path repoRoot = repoPath.toAbsolutePath().normalize();
        Path current = filePath.toAbsolutePath().normalize().getParent();
        while (current != null && current.startsWith(repoRoot)) {
            if (isModuleRoot(current)) {
                Path relative = repoRoot.relativize(current);
                if (relative.toString().isBlank()) {
                    return repoRoot.getFileName() != null ? repoRoot.getFileName().toString() : "root";
                }
                return relative.toString().replace("\\", "/");
            }
            current = current.getParent();
        }
        return repoRoot.getFileName() != null ? repoRoot.getFileName().toString() : "root";
    }

    private boolean isModuleRoot(Path directory) {
        return Files.exists(directory.resolve("pom.xml")) ||
               Files.exists(directory.resolve("build.gradle")) ||
               Files.exists(directory.resolve("build.gradle.kts"));
    }

    private int filePriority(String filename) {
        boolean isProfile = filename.startsWith("application-");
        boolean isProps = filename.endsWith(".properties");
        boolean isYaml = filename.endsWith(".yml") || filename.endsWith(".yaml");

        int base = 10;
        if (filename.equals("application.properties")) {
            base = 200;
        } else if (filename.equals("application.yml") || filename.equals("application.yaml")) {
            base = 150;
        } else if (isProps) {
            base = 100;
        } else if (isYaml) {
            base = 80;
        }

        if (isProfile) {
            base += 1000;
        }

        return base;
    }

    private static final class DefaultValue {
        private final String value;
        private final int priority;

        private DefaultValue(String value, int priority) {
            this.value = value;
            this.priority = priority;
        }
    }
}
