package com.example.envdoc.service;

import com.example.envdoc.dto.AnalysisRequest;
import com.example.envdoc.dto.AnalysisResponse;
import com.example.envdoc.model.AnalysisResult;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Информация о задаче анализа.
 */
@Data
public class AnalysisJob {
    private String id;
    private AnalysisRequest request;
    private volatile AnalysisResponse.AnalysisStatus status;
    private volatile int progress;
    private volatile String currentStep;
    private volatile String message;
    private LocalDateTime startedAt;
    private volatile AnalysisResult result;
}
