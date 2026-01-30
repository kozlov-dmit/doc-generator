package com.example.envdoc.service;

import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Определяет источник репозитория: локальный или удалённый.
 */
@Service
public class RepositoryResolver {
    private final BitBucketService bitBucketService;

    public RepositoryResolver(BitBucketService bitBucketService) {
        this.bitBucketService = bitBucketService;
    }

    public RepositoryHandle resolve(String repositoryUrl, String branch, String token) {
        Path localPath = resolveLocalPath(repositoryUrl);
        if (localPath != null) {
            if (!Files.isDirectory(localPath)) {
                throw new IllegalArgumentException("Local repository path is not a directory: " + localPath);
            }
            return new RepositoryHandle(localPath, localPath.getFileName().toString(), false, null);
        }

        Path repoPath = bitBucketService.cloneRepository(repositoryUrl, branch, token);
        String projectName = bitBucketService.extractProjectName(repositoryUrl);
        return new RepositoryHandle(repoPath, projectName, true, bitBucketService);
    }

    private Path resolveLocalPath(String repositoryUrl) {
        if (repositoryUrl == null || repositoryUrl.isBlank()) {
            return null;
        }

        if (repositoryUrl.startsWith("file://")) {
            return Path.of(URI.create(repositoryUrl));
        }

        try {
            Path path = Path.of(repositoryUrl);
            if (Files.exists(path)) {
                return path;
            }
        } catch (Exception ignored) {
            return null;
        }

        return null;
    }
}
