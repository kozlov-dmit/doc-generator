package com.example.envdoc.cli;

import com.example.envdoc.model.AnalysisResult;
import com.example.envdoc.service.analysis.AnalysisService;
import com.example.envdoc.service.confluence.ConfluencePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * CLI интерфейс для запуска анализа из командной строки.
 *
 * Примеры использования:
 *
 * java -jar env-doc-agent.jar --repo=https://bitbucket.org/company/project.git --output=./docs/ENV.md
 *
 * java -jar env-doc-agent.jar --repo=https://github.com/user/repo.git --branch=develop
 *
 * java -jar env-doc-agent.jar --repo=https://bitbucket.org/company/project.git \
 *   --confluence-space=DEVOPS --confluence-parent=123456
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommandLineRunner implements ApplicationRunner {

    private final AnalysisService analysisService;
    private final Optional<ConfluencePublisher> confluencePublisher;
    
    @Override
    public void run(ApplicationArguments args) throws Exception {
        // Проверяем, запущен ли CLI режим
        if (!args.containsOption("repo")) {
            // Если нет параметра --repo, значит запущен REST API режим
            log.info("Starting in REST API mode. Use --repo=<url> for CLI mode.");
            return;
        }

        log.info("Starting in CLI mode");

        try {
            runCli(args);
            // Завершаем приложение после выполнения CLI команды
            System.exit(0);
        } catch (Exception e) {
            log.error("CLI execution failed: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    private void runCli(ApplicationArguments args) {
        // Получаем параметры
        String repoUrl = getRequiredOption(args, "repo");
        String branch = getOption(args, "branch", null);
        String output = getOption(args, "output", "./output/ENV_VARIABLES.md");
        String token = getOption(args, "token", null);

        // Confluence параметры
        String confluenceSpace = getOption(args, "confluence-space", null);
        String confluenceParent = getOption(args, "confluence-parent", null);
        String confluenceTitle = getOption(args, "confluence-title", null);

        printBanner();

        System.out.println("Repository: " + repoUrl);
        System.out.println("Branch: " + (branch != null ? branch : "default"));
        System.out.println("Output: " + output);
        System.out.println();

        // Выполняем анализ
        System.out.println("Starting analysis...");
        System.out.println();

        AnalysisResult result = analysisService.analyzeSync(repoUrl, branch, token);

        // Сохраняем результат
        Path outputPath = Path.of(output);
        try {
            Path parent = outputPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(outputPath, result.getMarkdownContent());
            System.out.println("Documentation saved to: " + outputPath.toAbsolutePath());
        } catch (Exception e) {
            log.error("Failed to save documentation", e);
            throw new RuntimeException("Failed to save documentation: " + e.getMessage(), e);
        }

        // Публикация в Confluence
        if (confluenceSpace != null && confluencePublisher.isPresent()) {
            System.out.println();
            System.out.println("Publishing to Confluence...");

            String title = confluenceTitle != null
                    ? confluenceTitle
                    : "Переменные окружения: " + result.getProjectName();

            try {
                String pageUrl = confluencePublisher.get().publish(
                        result.getMarkdownContent(),
                        title,
                        confluenceSpace,
                        confluenceParent
                );
                System.out.println("Published to Confluence: " + pageUrl);
            } catch (Exception e) {
                log.error("Failed to publish to Confluence", e);
                System.err.println("Warning: Failed to publish to Confluence: " + e.getMessage());
            }
        }

        // Выводим статистику
        printSummary(result);
    }

    private void printBanner() {
        System.out.println();
        System.out.println("╔═══════════════════════════════════════════════════════════╗");
        System.out.println("║           EnvDoc Agent - Environment Variables            ║");
        System.out.println("║              Documentation Generator                       ║");
        System.out.println("╚═══════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    private void printSummary(AnalysisResult result) {
        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("                      ANALYSIS SUMMARY                      ");
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println();
        System.out.println("  Project:              " + result.getProjectName());
        System.out.println("  Total variables:      " + result.getTotalVariables());
        System.out.println("  Required variables:   " + result.getRequiredVariables());
        System.out.println("  Optional variables:   " + result.getOptionalVariables());
        System.out.println();

        if (!result.getVariables().isEmpty()) {
            System.out.println("  Variables found:");
            result.getVariables().forEach(var -> {
                String required = var.isRequired() ? " [REQUIRED]" : "";
                System.out.println("    - " + var.getName() + required);
            });
        }

        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("                    ANALYSIS COMPLETED                      ");
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println();
    }

    private String getRequiredOption(ApplicationArguments args, String name) {
        if (!args.containsOption(name)) {
            throw new IllegalArgumentException("Required option --" + name + " is missing");
        }
        return args.getOptionValues(name).get(0);
    }

    private String getOption(ApplicationArguments args, String name, String defaultValue) {
        if (args.containsOption(name)) {
            return args.getOptionValues(name).get(0);
        }
        return defaultValue;
    }
}
