package com.example.envdoc.service.extraction;

import com.example.envdoc.model.EnvVariable;
import com.example.envdoc.model.UsagePurpose;
import com.example.envdoc.model.VariableUsage;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Сервис для анализа использования переменных окружения в коде.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsageAnalyzer {

    private final SourceCodeAnalyzer sourceCodeAnalyzer;

    // Паттерны для определения цели использования
    private static final Map<Pattern, UsagePurpose> PURPOSE_PATTERNS = new LinkedHashMap<>();

    static {
        // Authentication & Security (check first - more specific)
        PURPOSE_PATTERNS.put(Pattern.compile("(?i)(security|auth|jwt|oauth|password|credential|secret)"),
                UsagePurpose.AUTHENTICATION);

        // Cache (check before database - redis is cache, not database)
        PURPOSE_PATTERNS.put(Pattern.compile("(?i)(cache|redis|ehcache|caffeine|hazelcast)"),
                UsagePurpose.CACHE_CONFIG);

        // Database
        PURPOSE_PATTERNS.put(Pattern.compile("(?i)(datasource|jdbc|hikari|database|db|postgres|mysql|oracle|mongo)"),
                UsagePurpose.DATABASE_CONNECTION);

        // External API
        PURPOSE_PATTERNS.put(Pattern.compile("(?i)(resttemplate|webclient|feign|http|client|endpoint|url|uri)"),
                UsagePurpose.EXTERNAL_API);

        // Feature flags
        PURPOSE_PATTERNS.put(Pattern.compile("(?i)(feature|flag|toggle|enabled|disabled)"),
                UsagePurpose.FEATURE_FLAG);

        // Logging
        PURPOSE_PATTERNS.put(Pattern.compile("(?i)(log|logging|logger|slf4j|logback)"),
                UsagePurpose.LOGGING_CONFIG);

        // Server config
        PURPOSE_PATTERNS.put(Pattern.compile("(?i)(server|port|host|address|ssl|tls)"),
                UsagePurpose.SERVER_CONFIG);

        // Messaging
        PURPOSE_PATTERNS.put(Pattern.compile("(?i)(kafka|rabbit|mq|jms|amqp|queue|topic|message)"),
                UsagePurpose.MESSAGING_CONFIG);
    }

    /**
     * Анализирует использование переменных окружения в репозитории.
     *
     * @param variables   Map с переменными окружения
     * @param repoPath    путь к репозиторию
     */
    public void analyzeUsages(Map<String, EnvVariable> variables, Path repoPath) {
        log.info("Analyzing variable usages in repository...");

        if (variables.isEmpty()) {
            return;
        }

        List<Path> javaFiles = sourceCodeAnalyzer.findJavaFiles(repoPath);
        Pattern placeholderPattern = buildPlaceholderPattern(variables.keySet());
        Pattern stringPattern = buildStringLiteralPattern(variables.keySet());

        for (Path javaFile : javaFiles) {
            String relativePath = repoPath.relativize(javaFile).toString();

            sourceCodeAnalyzer.parseJavaFile(javaFile).ifPresent(cu -> {
                String className = sourceCodeAnalyzer.extractFullClassName(cu);

                Map<String, List<VariableUsage>> usagesByVar =
                        findUsagesInFile(cu, variables.keySet(), relativePath, className,
                                placeholderPattern, stringPattern);

                usagesByVar.forEach((varName, usages) -> {
                    EnvVariable variable = variables.get(varName);
                    if (variable != null) {
                        variable.getUsages().addAll(usages);
                    }
                });
            });
        }

        // Дедупликация использований
        for (EnvVariable variable : variables.values()) {
            variable.setUsages(deduplicateUsages(variable.getUsages()));
        }

        log.info("Usage analysis completed");
    }

    /**
     * Находит использования переменной в файле.
     */
    private Map<String, List<VariableUsage>> findUsagesInFile(
            CompilationUnit cu,
            Set<String> variableNames,
            String filePath,
            String className,
            Pattern placeholderPattern,
            Pattern stringPattern) {

        Map<String, List<VariableUsage>> usages = new HashMap<>();

        // 1. Поля с @Value -> методы, которые используют поле
        findValueFieldUsages(cu, variableNames, filePath, className, usages);

        // 2. Прямые использования имени переменной
        findDirectUsages(cu, variableNames, filePath, className, usages,
                placeholderPattern, stringPattern);

        return usages;
    }

    /**
     * Находит использования через @Value поля.
     */
    private void findValueFieldUsages(CompilationUnit cu,
                                      Set<String> variableNames,
                                      String filePath,
                                      String className,
                                      Map<String, List<VariableUsage>> usages) {

        // Ищем поля с @Value, содержащие имя переменной
        List<FieldDeclaration> valueFields = sourceCodeAnalyzer.findFieldsWithAnnotation(cu, "Value");

        for (FieldDeclaration field : valueFields) {
            field.getAnnotationByName("Value").ifPresent(annotation -> {
                sourceCodeAnalyzer.extractValueAnnotationContent(annotation).ifPresent(content -> {
                    Set<String> matchedVars = extractVariablesFromPlaceholder(content);
                    if (matchedVars.isEmpty()) {
                        return;
                    }

                    // Найти все методы, использующие это поле
                    for (VariableDeclarator varDecl : field.getVariables()) {
                        String fieldName = varDecl.getNameAsString();

                        List<MethodDeclaration> methods =
                                sourceCodeAnalyzer.findMethodsUsingField(cu, fieldName);

                        for (MethodDeclaration method : methods) {
                            int lineNumber = method.getBegin().map(p -> p.line).orElse(0);
                            String methodName = method.getNameAsString();

                            // Определяем цель использования
                            UsagePurpose purpose = detectPurpose(className, methodName,
                                    method.toString());

                            // Формируем контекст
                            String context = buildUsageContext(className, method);

                            VariableUsage usage = VariableUsage.builder()
                                    .className(className)
                                    .methodName(methodName)
                                    .lineNumber(lineNumber)
                                    .filePath(filePath)
                                    .purpose(purpose)
                                    .usageContext(context)
                                    .codeSnippet(extractMethodSnippet(method))
                                    .build();

                            for (String varName : matchedVars) {
                                if (variableNames.contains(varName)) {
                                    usages.computeIfAbsent(varName, k -> new ArrayList<>()).add(usage);
                                }
                            }
                        }
                    }
                });
            });
        }
    }

    /**
     * Находит прямые использования переменной в коде.
     */
    private void findDirectUsages(CompilationUnit cu,
                                  Set<String> variableNames,
                                  String filePath,
                                  String className,
                                  Map<String, List<VariableUsage>> usages,
                                  Pattern placeholderPattern,
                                  Pattern stringPattern) {

        cu.findAll(MethodDeclaration.class).forEach(method -> {
            String methodBody = method.getBody().map(Object::toString).orElse("");

            Set<String> matchedVars = new LinkedHashSet<>();
            matchedVars.addAll(findMatches(placeholderPattern, methodBody));
            matchedVars.addAll(findMatches(stringPattern, methodBody));

            if (matchedVars.isEmpty()) {
                return;
            }

            int lineNumber = method.getBegin().map(p -> p.line).orElse(0);
            String methodName = method.getNameAsString();

            UsagePurpose purpose = detectPurpose(className, methodName, methodBody);
            String context = buildUsageContext(className, method);

            VariableUsage usage = VariableUsage.builder()
                    .className(className)
                    .methodName(methodName)
                    .lineNumber(lineNumber)
                    .filePath(filePath)
                    .purpose(purpose)
                    .usageContext(context)
                    .codeSnippet(extractMethodSnippet(method))
                    .build();

            for (String varName : matchedVars) {
                if (variableNames.contains(varName)) {
                    usages.computeIfAbsent(varName, k -> new ArrayList<>()).add(usage);
                }
            }
        });
    }

    /**
     * Определяет цель использования переменной.
     */
    public UsagePurpose detectPurpose(String className, String methodName, String context) {
        String combined = className + " " + methodName + " " + context;

        for (Map.Entry<Pattern, UsagePurpose> entry : PURPOSE_PATTERNS.entrySet()) {
            if (entry.getKey().matcher(combined).find()) {
                return entry.getValue();
            }
        }

        return UsagePurpose.OTHER;
    }

    /**
     * Формирует контекст использования.
     */
    private String buildUsageContext(String className, MethodDeclaration method) {
        StringBuilder context = new StringBuilder();

        // Извлекаем простое имя класса
        String simpleClassName = className.contains(".")
                ? className.substring(className.lastIndexOf('.') + 1)
                : className;

        // Анализируем аннотации метода
        List<String> annotations = new ArrayList<>();
        method.getAnnotations().forEach(a -> annotations.add(a.getNameAsString()));

        if (annotations.contains("Bean")) {
            context.append("Bean configuration: ");
        } else if (annotations.contains("PostConstruct")) {
            context.append("Initialization: ");
        } else if (annotations.contains("Scheduled")) {
            context.append("Scheduled task: ");
        }

        // Определяем тип компонента по имени класса
        if (simpleClassName.contains("Config")) {
            context.append("Configuration in ").append(simpleClassName);
        } else if (simpleClassName.contains("Service")) {
            context.append("Business logic in ").append(simpleClassName);
        } else if (simpleClassName.contains("Controller")) {
            context.append("HTTP endpoint in ").append(simpleClassName);
        } else if (simpleClassName.contains("Repository")) {
            context.append("Data access in ").append(simpleClassName);
        } else if (simpleClassName.contains("Health")) {
            context.append("Health check in ").append(simpleClassName);
        } else {
            context.append("Used in ").append(simpleClassName);
        }

        return context.toString();
    }

    /**
     * Извлекает фрагмент кода метода для документации.
     */
    private String extractMethodSnippet(MethodDeclaration method) {
        // Возвращаем сигнатуру метода без тела
        StringBuilder snippet = new StringBuilder();

        method.getAnnotations().forEach(a -> snippet.append(a.toString()).append("\n"));
        snippet.append(method.getDeclarationAsString());

        return snippet.toString();
    }

    /**
     * Удаляет дублирующиеся использования.
     */
    private List<VariableUsage> deduplicateUsages(List<VariableUsage> usages) {
        Map<String, VariableUsage> unique = new LinkedHashMap<>();

        for (VariableUsage usage : usages) {
            String key = usage.getClassName() + ":" + usage.getMethodName();
            if (!unique.containsKey(key)) {
                unique.put(key, usage);
            }
        }

        return new ArrayList<>(unique.values());
    }

    private static final Pattern PLACEHOLDER_PATTERN =
            Pattern.compile("\\$\\{([A-Za-z0-9_.-]+)(:[^}]*)?}");

    private Set<String> extractVariablesFromPlaceholder(String content) {
        Set<String> vars = new LinkedHashSet<>();
        var matcher = PLACEHOLDER_PATTERN.matcher(content);
        while (matcher.find()) {
            vars.add(matcher.group(1));
        }
        return vars;
    }

    private Pattern buildPlaceholderPattern(Set<String> variableNames) {
        if (variableNames.isEmpty()) {
            return Pattern.compile("$^");
        }
        String joined = variableNames.stream()
                .map(Pattern::quote)
                .collect(java.util.stream.Collectors.joining("|"));
        return Pattern.compile("\\$\\{(" + joined + ")\\}");
    }

    private Pattern buildStringLiteralPattern(Set<String> variableNames) {
        if (variableNames.isEmpty()) {
            return Pattern.compile("$^");
        }
        String joined = variableNames.stream()
                .map(Pattern::quote)
                .collect(java.util.stream.Collectors.joining("|"));
        return Pattern.compile("\"(" + joined + ")\"");
    }

    private Set<String> findMatches(Pattern pattern, String text) {
        Set<String> matches = new LinkedHashSet<>();
        var matcher = pattern.matcher(text);
        while (matcher.find()) {
            matches.add(matcher.group(1));
        }
        return matches;
    }
}
