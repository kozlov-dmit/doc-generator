package com.example.envdoc.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/**
 * Интеграционные тесты для Prometheus метрик.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MetricsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void prometheusEndpointShouldBeAccessible() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/plain"));
    }

    @Test
    void healthEndpointShouldBeAccessible() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void metricsEndpointShouldBeAccessible() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.names").isArray());
    }

    @Test
    void prometheusEndpointShouldContainJvmMetrics() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("jvm_memory")))
            .andExpect(content().string(containsString("jvm_threads")));
    }

    @Test
    void prometheusEndpointShouldContainApplicationTag() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("application=\"env-doc-agent\"")));
    }

    @Test
    void analysisMetricsShouldBeRegistered() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("analysis_jobs_active")))
            .andExpect(content().string(containsString("analysis_variables_count")))
            .andExpect(content().string(containsString("analysis_completed_total")))
            .andExpect(content().string(containsString("analysis_failed_total")));
    }
}
