package com.example.envdoc.controller;

import com.example.envdoc.dto.AnalysisRequest;
import com.example.envdoc.dto.AnalysisResponse;
import com.example.envdoc.service.AnalysisService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST API контроллер для анализа репозиториев.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;

    /**
     * Запускает анализ репозитория.
     *
     * POST /api/v1/analyze
     */
    @PostMapping("/analyze")
    public ResponseEntity<AnalysisResponse> startAnalysis(
            @RequestBody @Valid AnalysisRequest request) {

        log.info("Starting analysis for repository: {}", request.getRepositoryUrl());

        String jobId = analysisService.startAnalysis(request);

        AnalysisResponse response = AnalysisResponse.builder()
                .jobId(jobId)
                .status(AnalysisResponse.AnalysisStatus.PROCESSING)
                .message("Analysis started")
                .progress(0)
                .build();

        return ResponseEntity.accepted().body(response);
    }

    /**
     * Возвращает статус анализа.
     *
     * GET /api/v1/analyze/{jobId}
     */
    @GetMapping("/analyze/{jobId}")
    public ResponseEntity<AnalysisResponse> getStatus(@PathVariable String jobId) {

        log.debug("Getting status for job: {}", jobId);

        AnalysisResponse response = analysisService.getStatus(jobId);
        return ResponseEntity.ok(response);
    }

    /**
     * Скачивает сгенерированный файл документации.
     *
     * GET /api/v1/download/{jobId}/{filename}
     */
    @GetMapping("/download/{jobId}/{filename}")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable String jobId,
            @PathVariable String filename) {

        log.info("Downloading file {} for job {}", filename, jobId);

        Resource file = analysisService.getGeneratedFile(jobId, filename);

        String contentType = "text/markdown";
        if (filename.endsWith(".md")) {
            contentType = "text/markdown; charset=utf-8";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .body(file);
    }

    /**
     * Health check эндпоинт.
     *
     * GET /api/v1/health
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}
