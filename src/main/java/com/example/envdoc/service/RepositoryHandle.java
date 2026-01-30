package com.example.envdoc.service;

import java.nio.file.Path;

/**
 * Дескриптор локального репозитория (локальный или клонированный).
 */
public class RepositoryHandle implements AutoCloseable {
    private final Path path;
    private final String projectName;
    private final boolean cleanup;
    private final BitBucketService cleanupService;

    public RepositoryHandle(Path path, String projectName, boolean cleanup, BitBucketService cleanupService) {
        this.path = path;
        this.projectName = projectName;
        this.cleanup = cleanup;
        this.cleanupService = cleanupService;
    }

    public Path getPath() {
        return path;
    }

    public String getProjectName() {
        return projectName;
    }

    @Override
    public void close() {
        if (cleanup && cleanupService != null) {
            cleanupService.cleanupRepository(path);
        }
    }
}
