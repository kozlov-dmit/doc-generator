package com.example.envdoc.controller;

import com.example.envdoc.dto.AnalysisRequest;
import com.example.envdoc.dto.AnalysisResponse;
import com.example.envdoc.service.analysis.AnalysisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AnalysisController.class)
class AnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AnalysisService analysisService;

    @Test
    void shouldStartAnalysis() throws Exception {
        // Given
        String jobId = UUID.randomUUID().toString();
        when(analysisService.startAnalysis(any())).thenReturn(jobId);

        AnalysisRequest request = AnalysisRequest.builder()
                .repositoryUrl("https://github.com/test/repo.git")
                .branch("main")
                .outputFormats(List.of(AnalysisRequest.OutputFormat.MARKDOWN))
                .build();

        // When & Then
        mockMvc.perform(post("/api/v1/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value(jobId))
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.message").value("Analysis started"));
    }

    @Test
    void shouldReturnBadRequestForMissingRepositoryUrl() throws Exception {
        // Given
        AnalysisRequest request = AnalysisRequest.builder()
                .branch("main")
                .build();

        // When & Then
        mockMvc.perform(post("/api/v1/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnAnalysisStatus() throws Exception {
        // Given
        String jobId = UUID.randomUUID().toString();
        AnalysisResponse response = AnalysisResponse.builder()
                .jobId(jobId)
                .status(AnalysisResponse.AnalysisStatus.PROCESSING)
                .progress(50)
                .currentStep("Analyzing variables")
                .build();

        when(analysisService.getStatus(jobId)).thenReturn(response);

        // When & Then
        mockMvc.perform(get("/api/v1/analyze/{jobId}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId))
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.progress").value(50))
                .andExpect(jsonPath("$.currentStep").value("Analyzing variables"));
    }

    @Test
    void shouldReturnCompletedStatusWithResult() throws Exception {
        // Given
        String jobId = UUID.randomUUID().toString();
        AnalysisResponse.AnalysisResultDto resultDto = AnalysisResponse.AnalysisResultDto.builder()
                .projectName("test-project")
                .totalVariables(5)
                .requiredVariables(3)
                .optionalVariables(2)
                .markdownUrl("/api/v1/download/" + jobId + "/ENV_VARIABLES.md")
                .build();

        AnalysisResponse response = AnalysisResponse.builder()
                .jobId(jobId)
                .status(AnalysisResponse.AnalysisStatus.COMPLETED)
                .progress(100)
                .result(resultDto)
                .build();

        when(analysisService.getStatus(jobId)).thenReturn(response);

        // When & Then
        mockMvc.perform(get("/api/v1/analyze/{jobId}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.result.projectName").value("test-project"))
                .andExpect(jsonPath("$.result.totalVariables").value(5))
                .andExpect(jsonPath("$.result.requiredVariables").value(3));
    }

    @Test
    void shouldReturnFailedStatus() throws Exception {
        // Given
        String jobId = UUID.randomUUID().toString();
        AnalysisResponse response = AnalysisResponse.builder()
                .jobId(jobId)
                .status(AnalysisResponse.AnalysisStatus.FAILED)
                .message("Failed to clone repository")
                .build();

        when(analysisService.getStatus(jobId)).thenReturn(response);

        // When & Then
        mockMvc.perform(get("/api/v1/analyze/{jobId}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.message").value("Failed to clone repository"));
    }

    @Test
    void shouldReturnNotFoundForUnknownJob() throws Exception {
        // Given
        String unknownJobId = UUID.randomUUID().toString();
        AnalysisResponse response = AnalysisResponse.builder()
                .jobId(unknownJobId)
                .status(AnalysisResponse.AnalysisStatus.FAILED)
                .message("Job not found")
                .build();

        when(analysisService.getStatus(unknownJobId)).thenReturn(response);

        // When & Then
        mockMvc.perform(get("/api/v1/analyze/{jobId}", unknownJobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.message").value("Job not found"));
    }

    @Test
    void shouldReturnHealthStatus() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }
}
