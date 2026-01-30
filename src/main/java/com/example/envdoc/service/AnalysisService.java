package com.example.envdoc.service;

import com.example.envdoc.dto.AnalysisRequest;
import com.example.envdoc.dto.AnalysisResponse;
import com.example.envdoc.dto.EnvVariableDto;
import com.example.envdoc.metrics.AnalysisMetrics;
import com.example.envdoc.model.AnalysisResult;
import com.example.envdoc.model.EnvVariable;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Основной сервис для управления процессом анализа.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final BitBucketService bitBucketService;
    private final EnvVarExtractor envVarExtractor;
    private final UsageAnalyzer usageAnalyzer;
    private final GigaChatService gigaChatService;
    private final DocumentGenerator documentGenerator;
    private final Optional<ConfluencePublisher> confluencePublisher;
    private final TaskExecutor taskExecutor;
    private final AnalysisMetrics analysisMetrics;

    // Хранилище статусов и результатов анализа
    private final Map<String, AnalysisJob> jobs = new ConcurrentHashMap<>();

    /**
     * Запускает асинхронный анализ репозитория.
     *
     * @param request параметры анализа
     * @return ID задачи
     */
    public String startAnalysis(AnalysisRequest request) {
        String jobId = UUID.randomUUID().toString();

        AnalysisJob job = new AnalysisJob();
        job.setId(jobId);
        job.setRequest(request);
        job.setStatus(AnalysisResponse.AnalysisStatus.PENDING);
        job.setProgress(0);
        job.setStartedAt(LocalDateTime.now());

        jobs.put(jobId, job);

        // Запускаем асинхронную обработку
        taskExecutor.execute(() -> runAnalysis(jobId));

        return jobId;
    }

    /**
     * Выполняет синхронный анализ (для CLI).
     *
     * @param repoUrl  URL репозитория
     * @param branch   ветка
     * @param token    токен доступа
     * @return результат анализа
     */
    public AnalysisResult analyzeSync(String repoUrl, String branch, String token) {
        Timer.Sample totalSample = analysisMetrics.startTimer();
        analysisMetrics.incrementActiveJobs();
        Path repoPath = null;

        try {
            // 1. Клонирование
            log.info("Step 1/5: Cloning repository...");
            Timer.Sample cloneSample = analysisMetrics.startTimer();
            repoPath = bitBucketService.cloneRepository(repoUrl, branch, token);
            String projectName = bitBucketService.extractProjectName(repoUrl);
            analysisMetrics.recordStepDuration(cloneSample, "clone");

            // 2. Извлечение переменных
            log.info("Step 2/5: Extracting environment variables...");
            Timer.Sample extractSample = analysisMetrics.startTimer();
            Map<String, EnvVariable> variables = envVarExtractor.extractAllVariables(repoPath);
            analysisMetrics.recordStepDuration(extractSample, "extract");

            // 3. Анализ использования
            log.info("Step 3/5: Analyzing variable usages...");
            Timer.Sample analyzeSample = analysisMetrics.startTimer();
            usageAnalyzer.analyzeUsages(variables, repoPath);
            analysisMetrics.recordStepDuration(analyzeSample, "analyze");

            // 4. Генерация документации через GigaChat
            log.info("Step 4/5: Generating documentation with GigaChat...");
            Timer.Sample generateSample = analysisMetrics.startTimer();
            List<EnvVariable> varList = new ArrayList<>(variables.values());
            String markdownContent = gigaChatService.generateDocumentation(varList, projectName, repoPath);
            analysisMetrics.recordStepDuration(generateSample, "generate");

            // 5. Формирование результата
            log.info("Step 5/5: Preparing result...");
            Timer.Sample saveSample = analysisMetrics.startTimer();
            AnalysisResult result = AnalysisResult.builder()
                    .projectName(projectName)
                    .repositoryUrl(repoUrl)
                    .branch(branch)
                    .startedAt(LocalDateTime.now())
                    .completedAt(LocalDateTime.now())
                    .variables(varList)
                    .markdownContent(markdownContent)
                    .build();

            // Сохранение в файл
            Path outputPath = documentGenerator.generateAndSave(result);
            result.setMarkdownFilePath(outputPath.toString());
            analysisMetrics.recordStepDuration(saveSample, "save");

            analysisMetrics.setVariablesCount(varList.size());
            analysisMetrics.recordCompleted();
            log.info("Analysis completed: {} variables found", result.getTotalVariables());
            return result;

        } catch (Exception e) {
            analysisMetrics.recordFailed();
            throw e;
        } finally {
            analysisMetrics.decrementActiveJobs();
            analysisMetrics.recordTotalDuration(totalSample);
            if (repoPath != null) {
                bitBucketService.cleanupRepository(repoPath);
            }
        }
    }

    /**
     * Возвращает статус задачи анализа.
     *
     * @param jobId ID задачи
     * @return статус и результат
     */
    public AnalysisResponse getStatus(String jobId) {
        AnalysisJob job = jobs.get(jobId);

        if (job == null) {
            return AnalysisResponse.builder()
                    .jobId(jobId)
                    .status(AnalysisResponse.AnalysisStatus.FAILED)
                    .message("Job not found")
                    .build();
        }

        AnalysisResponse.AnalysisResponseBuilder builder = AnalysisResponse.builder()
                .jobId(jobId)
                .status(job.getStatus())
                .progress(job.getProgress())
                .currentStep(job.getCurrentStep())
                .message(job.getMessage());

        if (job.getStatus() == AnalysisResponse.AnalysisStatus.COMPLETED && job.getResult() != null) {
            builder.result(convertToResultDto(job));
        }

        return builder.build();
    }

    /**
     * Возвращает сгенерированный файл.
     *
     * @param jobId    ID задачи
     * @param filename имя файла
     * @return Resource файла
     */
    public Resource getGeneratedFile(String jobId, String filename) {
        AnalysisJob job = jobs.get(jobId);

        if (job == null || job.getResult() == null) {
            throw new RuntimeException("Job or result not found");
        }

        Path filePath = Path.of(job.getResult().getMarkdownFilePath());

        if (!Files.exists(filePath)) {
            throw new RuntimeException("File not found: " + filename);
        }

        return new FileSystemResource(filePath);
    }

    void runAnalysis(String jobId) {
        AnalysisJob job = jobs.get(jobId);
        if (job == null) return;

        Timer.Sample totalSample = analysisMetrics.startTimer();
        analysisMetrics.incrementActiveJobs();
        Path repoPath = null;

        try {
            job.setStatus(AnalysisResponse.AnalysisStatus.PROCESSING);

            // 1. Клонирование репозитория
            updateJobProgress(job, 10, "Cloning repository...");
            Timer.Sample cloneSample = analysisMetrics.startTimer();
            repoPath = bitBucketService.cloneRepository(
                    job.getRequest().getRepositoryUrl(),
                    job.getRequest().getBranch(),
                    job.getRequest().getBitbucketToken()
            );
            String projectName = bitBucketService.extractProjectName(job.getRequest().getRepositoryUrl());
            analysisMetrics.recordStepDuration(cloneSample, "clone");

            // 2. Извлечение переменных
            updateJobProgress(job, 30, "Extracting environment variables...");
            Timer.Sample extractSample = analysisMetrics.startTimer();
            Map<String, EnvVariable> variables = envVarExtractor.extractAllVariables(repoPath);
            analysisMetrics.recordStepDuration(extractSample, "extract");

            // 3. Анализ использования
            updateJobProgress(job, 50, "Analyzing variable usages...");
            Timer.Sample analyzeSample = analysisMetrics.startTimer();
            usageAnalyzer.analyzeUsages(variables, repoPath);
            analysisMetrics.recordStepDuration(analyzeSample, "analyze");

            // 4. Генерация документации
            updateJobProgress(job, 70, "Generating documentation with GigaChat...");
            Timer.Sample generateSample = analysisMetrics.startTimer();
            List<EnvVariable> varList = new ArrayList<>(variables.values());
            String markdownContent = gigaChatService.generateDocumentation(varList, projectName, repoPath);
            analysisMetrics.recordStepDuration(generateSample, "generate");

            // 5. Сохранение результатов
            updateJobProgress(job, 85, "Saving documentation...");
            Timer.Sample saveSample = analysisMetrics.startTimer();
            AnalysisResult result = AnalysisResult.builder()
                    .projectName(projectName)
                    .repositoryUrl(job.getRequest().getRepositoryUrl())
                    .branch(job.getRequest().getBranch())
                    .startedAt(job.getStartedAt())
                    .completedAt(LocalDateTime.now())
                    .variables(varList)
                    .markdownContent(markdownContent)
                    .build();

            // Сохранение Markdown
            if (shouldGenerateMarkdown(job.getRequest())) {
                Path outputPath = documentGenerator.generateAndSave(result);
                result.setMarkdownFilePath(outputPath.toString());
            }
            analysisMetrics.recordStepDuration(saveSample, "save");

            // 6. Публикация в Confluence
            if (shouldPublishToConfluence(job.getRequest())) {
                updateJobProgress(job, 95, "Publishing to Confluence...");
                Timer.Sample publishSample = analysisMetrics.startTimer();
                publishToConfluence(result, job.getRequest());
                analysisMetrics.recordStepDuration(publishSample, "publish");
            }

            // Завершение
            job.setResult(result);
            job.setStatus(AnalysisResponse.AnalysisStatus.COMPLETED);
            job.setProgress(100);
            job.setCurrentStep("Completed");

            analysisMetrics.setVariablesCount(varList.size());
            analysisMetrics.recordCompleted();
            log.info("Job {} completed: {} variables found", jobId, result.getTotalVariables());

        } catch (Exception e) {
            log.error("Job {} failed: {}", jobId, e.getMessage(), e);
            job.setStatus(AnalysisResponse.AnalysisStatus.FAILED);
            job.setMessage(e.getMessage());
            analysisMetrics.recordFailed();
        } finally {
            analysisMetrics.decrementActiveJobs();
            analysisMetrics.recordTotalDuration(totalSample);
            if (repoPath != null) {
                bitBucketService.cleanupRepository(repoPath);
            }
        }
    }

    private void updateJobProgress(AnalysisJob job, int progress, String step) {
        job.setProgress(progress);
        job.setCurrentStep(step);
        log.debug("Job {}: {}% - {}", job.getId(), progress, step);
    }

    private boolean shouldGenerateMarkdown(AnalysisRequest request) {
        return request.getOutputFormats() == null ||
               request.getOutputFormats().isEmpty() ||
               request.getOutputFormats().contains(AnalysisRequest.OutputFormat.MARKDOWN);
    }

    private boolean shouldPublishToConfluence(AnalysisRequest request) {
        return request.getOutputFormats() != null &&
               request.getOutputFormats().contains(AnalysisRequest.OutputFormat.CONFLUENCE) &&
               confluencePublisher.isPresent();
    }

    private void publishToConfluence(AnalysisResult result, AnalysisRequest request) {
        if (confluencePublisher.isEmpty()) {
            log.warn("Confluence publisher not available");
            return;
        }

        AnalysisRequest.ConfluenceConfig confConfig = request.getConfluenceConfig();
        String spaceKey = confConfig != null && confConfig.getSpaceKey() != null
                ? confConfig.getSpaceKey()
                : "DOCS";

        String parentPageId = confConfig != null ? confConfig.getParentPageId() : null;

        String title = confConfig != null && confConfig.getPageTitle() != null
                ? confConfig.getPageTitle()
                : "Переменные окружения: " + result.getProjectName();

        String pageUrl = confluencePublisher.get().publish(
                result.getMarkdownContent(),
                title,
                spaceKey,
                parentPageId
        );

        result.setConfluencePageUrl(pageUrl);
    }

    private AnalysisResponse.AnalysisResultDto convertToResultDto(AnalysisJob job) {
        AnalysisResult result = job.getResult();

        List<EnvVariableDto> variableDtos = result.getVariables().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());

        String markdownUrl = result.getMarkdownFilePath() != null
                ? "/api/v1/download/" + job.getId() + "/" +
                  Path.of(result.getMarkdownFilePath()).getFileName()
                : null;

        return AnalysisResponse.AnalysisResultDto.builder()
                .projectName(result.getProjectName())
                .totalVariables(result.getTotalVariables())
                .requiredVariables(result.getRequiredVariables())
                .optionalVariables(result.getOptionalVariables())
                .variables(variableDtos)
                .markdownUrl(markdownUrl)
                .confluencePageUrl(result.getConfluencePageUrl())
                .build();
    }

    private EnvVariableDto convertToDto(EnvVariable var) {
        EnvVariableDto.DefinitionDto definitionDto = null;
        if (var.getDefinition() != null) {
            definitionDto = EnvVariableDto.DefinitionDto.builder()
                    .type(var.getDefinition().getType())
                    .filePath(var.getDefinition().getFilePath())
                    .lineNumber(var.getDefinition().getLineNumber())
                    .codeSnippet(var.getDefinition().getCodeSnippet())
                    .build();
        }

        List<EnvVariableDto.UsageDto> usageDtos = var.getUsages() != null
                ? var.getUsages().stream()
                        .map(u -> EnvVariableDto.UsageDto.builder()
                                .className(u.getClassName())
                                .methodName(u.getMethodName())
                                .lineNumber(u.getLineNumber())
                                .purpose(u.getPurpose())
                                .context(u.getUsageContext())
                                .build())
                        .collect(Collectors.toList())
                : List.of();

        return EnvVariableDto.builder()
                .name(var.getName())
                .description(var.getDescription())
                .type(var.getDataType())
                .required(var.isRequired())
                .defaultValue(var.getDefaultValue())
                .example(var.getExampleValue())
                .category(var.getCategory())
                .definition(definitionDto)
                .usages(usageDtos)
                .build();
    }

    /**
     * Внутренний класс для хранения информации о задаче.
     */
    @lombok.Data
    private static class AnalysisJob {
        private String id;
        private AnalysisRequest request;
        private volatile AnalysisResponse.AnalysisStatus status;
        private volatile int progress;
        private volatile String currentStep;
        private volatile String message;
        private LocalDateTime startedAt;
        private volatile AnalysisResult result;
    }
}
