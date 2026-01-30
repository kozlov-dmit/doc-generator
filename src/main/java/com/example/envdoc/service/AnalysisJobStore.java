package com.example.envdoc.service;

import com.example.envdoc.dto.AnalysisRequest;
import com.example.envdoc.dto.AnalysisResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Хранилище задач анализа.
 */
@Service
public class AnalysisJobStore {
    private final Map<String, AnalysisJob> jobs = new ConcurrentHashMap<>();

    public AnalysisJob create(AnalysisRequest request) {
        String jobId = UUID.randomUUID().toString();
        AnalysisJob job = new AnalysisJob();
        job.setId(jobId);
        job.setRequest(request);
        job.setStatus(AnalysisResponse.AnalysisStatus.PENDING);
        job.setProgress(0);
        job.setStartedAt(LocalDateTime.now());
        jobs.put(jobId, job);
        return job;
    }

    public AnalysisJob get(String jobId) {
        return jobs.get(jobId);
    }
}
