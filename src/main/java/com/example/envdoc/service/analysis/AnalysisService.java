package com.example.envdoc.service.analysis;

import com.example.envdoc.dto.AnalysisRequest;
import com.example.envdoc.dto.AnalysisResponse;
import com.example.envdoc.model.AnalysisResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Основной сервис для управления процессом анализа.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final TaskExecutor taskExecutor;
    private final AnalysisJobStore jobStore;
    private final AnalysisWorkflow analysisWorkflow;
    private final AnalysisResultMapper resultMapper;

    /**
     * Запускает асинхронный анализ репозитория.
     *
     * @param request параметры анализа
     * @return ID задачи
     */
    public String startAnalysis(AnalysisRequest request) {
        AnalysisJob job = jobStore.create(request);
        taskExecutor.execute(() -> runAnalysis(job.getId()));
        return job.getId();
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
        AnalysisRequest request = AnalysisRequest.builder()
                .repositoryUrl(repoUrl)
                .branch(branch)
                .bitbucketToken(token)
                .build();

        AnalysisResult result = analysisWorkflow.analyze(
                request,
                true,
                false,
                null
        );
        log.info("Analysis completed: {} variables found", result.getTotalVariables());
        return result;
    }

    /**
     * Возвращает статус задачи анализа.
     *
     * @param jobId ID задачи
     * @return статус и результат
     */
    public AnalysisResponse getStatus(String jobId) {
        AnalysisJob job = jobStore.get(jobId);

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
            builder.result(resultMapper.toDto(job));
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
        AnalysisJob job = jobStore.get(jobId);

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
        AnalysisJob job = jobStore.get(jobId);
        if (job == null) return;

        try {
            job.setStatus(AnalysisResponse.AnalysisStatus.PROCESSING);

            AnalysisResult result = analysisWorkflow.analyze(
                    job.getRequest(),
                    shouldGenerateMarkdown(job.getRequest()),
                    shouldPublishToConfluence(job.getRequest()),
                    (progress, step) -> updateJobProgress(job, progress, step)
            );

            job.setResult(result);
            job.setStatus(AnalysisResponse.AnalysisStatus.COMPLETED);
            job.setProgress(100);
            job.setCurrentStep("Completed");
            log.info("Job {} completed: {} variables found", jobId, result.getTotalVariables());

        } catch (Exception e) {
            log.error("Job {} failed: {}", jobId, e.getMessage(), e);
            job.setStatus(AnalysisResponse.AnalysisStatus.FAILED);
            job.setMessage(e.getMessage());
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
               request.getOutputFormats().contains(AnalysisRequest.OutputFormat.CONFLUENCE);
    }

}
