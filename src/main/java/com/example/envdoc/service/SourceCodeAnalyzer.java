package com.example.envdoc.service;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Сервис для анализа исходного кода Java файлов.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SourceCodeAnalyzer {

    private final JavaParser javaParser = new JavaParser();

    /**
     * Находит все Java файлы в репозитории.
     *
     * @param repoPath путь к репозиторию
     * @return список путей к Java файлам
     */
    public List<Path> findJavaFiles(Path repoPath) {
        try (Stream<Path> paths = Files.walk(repoPath)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        String path = normalizePath(p);
                        return !path.contains("/test/") && !path.contains("/target/");
                    })
                    .toList();
        } catch (IOException e) {
            log.error("Error finding Java files", e);
            return List.of();
        }
    }

    /**
     * Находит все YAML/Properties файлы конфигурации.
     *
     * @param repoPath путь к репозиторию
     * @return список путей к конфигурационным файлам
     */
    public List<Path> findConfigFiles(Path repoPath) {
        try (Stream<Path> paths = Files.walk(repoPath)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String fileName = p.getFileName().toString().toLowerCase();
                        return fileName.endsWith(".yml") ||
                               fileName.endsWith(".yaml") ||
                               fileName.endsWith(".properties");
                    })
                    .filter(p -> {
                        String path = normalizePath(p);
                        return !path.contains("/target/") && !path.contains("/test/");
                    })
                    .toList();
        } catch (IOException e) {
            log.error("Error finding config files", e);
            return List.of();
        }
    }

    /**
     * Парсит Java файл и возвращает AST.
     *
     * @param javaFile путь к Java файлу
     * @return Optional с CompilationUnit
     */
    public Optional<CompilationUnit> parseJavaFile(Path javaFile) {
        try {
            ParseResult<CompilationUnit> result = javaParser.parse(javaFile);
            if (result.isSuccessful() && result.getResult().isPresent()) {
                return result.getResult();
            } else {
                log.warn("Failed to parse {}: {}", javaFile, result.getProblems());
                return Optional.empty();
            }
        } catch (IOException e) {
            log.error("Error reading file {}", javaFile, e);
            return Optional.empty();
        }
    }

    /**
     * Читает содержимое файла.
     *
     * @param filePath путь к файлу
     * @return содержимое файла
     */
    public String readFileContent(Path filePath) {
        try {
            return Files.readString(filePath);
        } catch (IOException e) {
            log.error("Error reading file {}", filePath, e);
            return "";
        }
    }

    /**
     * Читает указанные строки из файла.
     *
     * @param filePath путь к файлу
     * @param startLine начальная строка (1-based)
     * @param endLine конечная строка (1-based)
     * @return фрагмент кода
     */
    public String readLines(Path filePath, int startLine, int endLine) {
        try {
            List<String> lines = Files.readAllLines(filePath);
            int start = Math.max(0, startLine - 1);
            int end = Math.min(lines.size(), endLine);
            return String.join("\n", lines.subList(start, end));
        } catch (IOException e) {
            log.error("Error reading lines from {}", filePath, e);
            return "";
        }
    }

    /**
     * Получает контекст вокруг указанной строки.
     *
     * @param filePath путь к файлу
     * @param lineNumber номер строки
     * @param contextLines количество строк контекста
     * @return фрагмент кода с контекстом
     */
    public String getCodeContext(Path filePath, int lineNumber, int contextLines) {
        return readLines(filePath, lineNumber - contextLines, lineNumber + contextLines);
    }

    /**
     * Извлекает полное имя класса из CompilationUnit.
     *
     * @param cu CompilationUnit
     * @return полное имя класса
     */
    public String extractFullClassName(CompilationUnit cu) {
        String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");

        String className = cu.getPrimaryTypeName().orElse("Unknown");

        return packageName.isEmpty() ? className : packageName + "." + className;
    }

    /**
     * Находит все методы, использующие указанное поле.
     *
     * @param cu CompilationUnit
     * @param fieldName имя поля
     * @return список методов
     */
    public List<MethodDeclaration> findMethodsUsingField(CompilationUnit cu, String fieldName) {
        List<MethodDeclaration> methods = new ArrayList<>();

        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodDeclaration md, Void arg) {
                super.visit(md, arg);
                if (md.getBody().isPresent()) {
                    String body = md.getBody().get().toString();
                    if (body.contains(fieldName)) {
                        methods.add(md);
                    }
                }
            }
        }, null);

        return methods;
    }

    /**
     * Находит все вызовы методов с указанным именем.
     *
     * @param cu CompilationUnit
     * @param methodName имя метода
     * @return список вызовов методов
     */
    public List<MethodCallExpr> findMethodCalls(CompilationUnit cu, String methodName) {
        List<MethodCallExpr> calls = new ArrayList<>();

        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodCallExpr mce, Void arg) {
                super.visit(mce, arg);
                if (mce.getNameAsString().equals(methodName)) {
                    calls.add(mce);
                }
            }
        }, null);

        return calls;
    }

    /**
     * Проверяет, имеет ли класс указанную аннотацию.
     *
     * @param cu CompilationUnit
     * @param annotationName имя аннотации
     * @return true если аннотация присутствует
     */
    public boolean hasClassAnnotation(CompilationUnit cu, String annotationName) {
        return cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                .anyMatch(c -> c.getAnnotations().stream()
                        .anyMatch(a -> a.getNameAsString().equals(annotationName) ||
                                       a.getNameAsString().endsWith("." + annotationName)));
    }

    /**
     * Находит поля с указанной аннотацией.
     *
     * @param cu CompilationUnit
     * @param annotationName имя аннотации
     * @return список полей
     */
    public List<FieldDeclaration> findFieldsWithAnnotation(CompilationUnit cu, String annotationName) {
        return cu.findAll(FieldDeclaration.class).stream()
                .filter(f -> f.getAnnotations().stream()
                        .anyMatch(a -> a.getNameAsString().equals(annotationName) ||
                                       a.getNameAsString().endsWith("." + annotationName)))
                .toList();
    }

    /**
     * Извлекает значение из аннотации @Value.
     *
     * @param annotation аннотация
     * @return значение аннотации (например, "${DATABASE_URL}")
     */
    public Optional<String> extractValueAnnotationContent(AnnotationExpr annotation) {
        if (annotation.isSingleMemberAnnotationExpr()) {
            return Optional.of(annotation.asSingleMemberAnnotationExpr()
                    .getMemberValue().toString()
                    .replace("\"", ""));
        } else if (annotation.isNormalAnnotationExpr()) {
            return annotation.asNormalAnnotationExpr().getPairs().stream()
                    .filter(p -> p.getNameAsString().equals("value"))
                    .findFirst()
                    .map(p -> p.getValue().toString().replace("\"", ""));
        }
        return Optional.empty();
    }

    private String normalizePath(Path path) {
        return path.toString().replace('\\', '/');
    }
}
