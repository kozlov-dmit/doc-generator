package com.example.envdoc.service;

import com.example.envdoc.dto.AnalysisRequest;
import com.example.envdoc.metrics.AnalysisMetrics;
import com.example.envdoc.model.AnalysisResult;
import com.example.envdoc.model.EnvVariable;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Основной workflow анализа.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisWorkflow {
    private final RepositoryResolver repositoryResolver;
    private final EnvVarExtractor envVarExtractor;
    private final UsageAnalyzer usageAnalyzer;
    private final GigaChatService gigaChatService;
    private final DocumentGenerator documentGenerator;
    private final ConfluencePublishService confluencePublishService;
    private final AnalysisMetrics analysisMetrics;

    public AnalysisResult analyze(AnalysisRequest request,
                                  boolean generateMarkdown,
                                  boolean publishConfluence,
                                  AnalysisProgressListener listener) {
        Timer.Sample totalSample = analysisMetrics.startTimer();
        analysisMetrics.incrementActiveJobs();

        Timer.Sample cloneSample = analysisMetrics.startTimer();
        notify(listener, 10, "Resolving repository...");
        try (RepositoryHandle repository = repositoryResolver.resolve(
                request.getRepositoryUrl(),
                request.getBranch(),
                request.getBitbucketToken()
        )) {
            analysisMetrics.recordStepDuration(cloneSample, "clone");
            String projectName = repository.getProjectName();

            // 1. Извлечение переменных
            notify(listener, 30, "Extracting environment variables...");
            Timer.Sample extractSample = analysisMetrics.startTimer();
            Map<String, EnvVariable> variables = envVarExtractor.extractAllVariables(repository.getPath());
            analysisMetrics.recordStepDuration(extractSample, "extract");

            // 2. Анализ использования
            notify(listener, 50, "Analyzing variable usages...");
            Timer.Sample analyzeSample = analysisMetrics.startTimer();
            usageAnalyzer.analyzeUsages(variables, repository.getPath());
            analysisMetrics.recordStepDuration(analyzeSample, "analyze");

            // 3. Генерация документации
            notify(listener, 70, "Generating documentation with GigaChat...");
            Timer.Sample generateSample = analysisMetrics.startTimer();
            List<EnvVariable> varList = new ArrayList<>(variables.values());
            String markdownContent = gigaChatService.generateDocumentation(varList, projectName, repository.getPath());
            analysisMetrics.recordStepDuration(generateSample, "generate");

            // 4. Сохранение результата
            notify(listener, 85, "Saving documentation...");
            Timer.Sample saveSample = analysisMetrics.startTimer();
            AnalysisResult result = AnalysisResult.builder()
                    .projectName(projectName)
                    .repositoryUrl(request.getRepositoryUrl())
                    .branch(request.getBranch())
                    .startedAt(LocalDateTime.now())
                    .completedAt(LocalDateTime.now())
                    .variables(varList)
                    .markdownContent(markdownContent)
                    .build();

            if (generateMarkdown) {
                var outputPath = documentGenerator.generateAndSave(result);
                result.setMarkdownFilePath(outputPath.toString());
            }
            analysisMetrics.recordStepDuration(saveSample, "save");

            if (publishConfluence && confluencePublishService.canPublish()) {
                notify(listener, 95, "Publishing to Confluence...");
                Timer.Sample publishSample = analysisMetrics.startTimer();
                confluencePublishService.publish(result, request);
                analysisMetrics.recordStepDuration(publishSample, "publish");
            }

            analysisMetrics.setVariablesCount(varList.size());
            analysisMetrics.recordCompleted();
            return result;

        } catch (Exception e) {
            analysisMetrics.recordFailed();
            throw e;
        } finally {
            analysisMetrics.decrementActiveJobs();
            analysisMetrics.recordTotalDuration(totalSample);
        }
    }

    private void notify(AnalysisProgressListener listener, int progress, String step) {
        if (listener != null) {
            listener.onProgress(progress, step);
        }
    }
}
