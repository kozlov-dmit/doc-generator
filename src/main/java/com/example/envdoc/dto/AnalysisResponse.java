package com.example.envdoc.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Ответ с результатами анализа.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisResponse {
    /**
     * ID задачи анализа
     */
    private String jobId;

    /**
     * Статус выполнения
     */
    private AnalysisStatus status;

    /**
     * Прогресс выполнения (0-100)
     */
    private int progress;

    /**
     * Текущий шаг выполнения
     */
    private String currentStep;

    /**
     * Сообщение (для ошибок или информации)
     */
    private String message;

    /**
     * Результат анализа (заполняется после завершения)
     */
    private AnalysisResultDto result;

    public enum AnalysisStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalysisResultDto {
        private String projectName;
        private int totalVariables;
        private long requiredVariables;
        private long optionalVariables;
        private List<EnvVariableDto> variables;
        private String markdownUrl;
        private String confluencePageUrl;
    }
}
