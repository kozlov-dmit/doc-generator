package com.example.envdoc.service;

import com.example.envdoc.config.AppConfig;
import com.example.envdoc.config.BitBucketConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Сервис для клонирования репозиториев из BitBucket и других Git хостингов.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BitBucketService {

    private final AppConfig appConfig;
    private final BitBucketConfig bitBucketConfig;

    /**
     * Клонирует репозиторий и возвращает путь к локальной копии.
     *
     * @param repoUrl URL репозитория
     * @param branch  ветка для клонирования (null для default branch)
     * @param token   токен для аутентификации (null для использования глобального)
     * @return путь к склонированному репозиторию
     */
    public Path cloneRepository(String repoUrl, String branch, String token) {
        log.info("Cloning repository: {} (branch: {})", repoUrl, branch != null ? branch : "default");

        Path targetDir = createTempDirectory();

        try {
            CloneCommand cloneCommand = Git.cloneRepository()
                    .setURI(repoUrl)
                    .setDirectory(targetDir.toFile())
                    .setDepth(1) // Shallow clone для ускорения
                    .setTimeout(bitBucketConfig.getCloneTimeoutSeconds());

            // Установка ветки
            if (branch != null && !branch.isBlank()) {
                cloneCommand.setBranch(branch);
            }

            // Настройка аутентификации
            CredentialsProvider credentials = createCredentialsProvider(token);
            if (credentials != null) {
                cloneCommand.setCredentialsProvider(credentials);
            }

            try (Git git = cloneCommand.call()) {
                log.info("Repository cloned successfully to: {}", targetDir);
                validateRepositorySize(targetDir);
                return targetDir;
            }

        } catch (GitAPIException e) {
            log.error("Failed to clone repository: {}", e.getMessage(), e);
            cleanupDirectory(targetDir);
            throw new RuntimeException("Failed to clone repository: " + e.getMessage(), e);
        }
    }

    /**
     * Извлекает имя проекта из URL репозитория.
     *
     * @param repoUrl URL репозитория
     * @return имя проекта
     */
    public String extractProjectName(String repoUrl) {
        // Удаляем .git в конце если есть
        String url = repoUrl.replaceAll("\\.git$", "");

        // Извлекаем последнюю часть URL
        int lastSlash = url.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < url.length() - 1) {
            return url.substring(lastSlash + 1);
        }

        return "unknown-project";
    }

    /**
     * Удаляет клонированный репозиторий.
     *
     * @param repoPath путь к репозиторию
     */
    public void cleanupRepository(Path repoPath) {
        if (repoPath != null) {
            cleanupDirectory(repoPath);
        }
    }

    private Path createTempDirectory() {
        try {
            Path tempDir = Path.of(appConfig.getTempDir());
            Files.createDirectories(tempDir);
            cleanupOldRepositories(tempDir);

            String uniqueDir = "repo-" + UUID.randomUUID();
            Path targetDir = tempDir.resolve(uniqueDir);
            Files.createDirectories(targetDir);

            return targetDir;
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temp directory", e);
        }
    }

    private CredentialsProvider createCredentialsProvider(String token) {
        // Приоритет: переданный токен -> глобальный токен
        String effectiveToken = token != null ? token : bitBucketConfig.getToken();

        if (effectiveToken == null || effectiveToken.isBlank()) {
            return null;
        }

        // Для BitBucket App Password используется username:token
        String username = bitBucketConfig.getUsername();
        if (username != null && !username.isBlank()) {
            return new UsernamePasswordCredentialsProvider(username, effectiveToken);
        }

        // Для других систем (GitHub, GitLab) можно использовать токен как username
        return new UsernamePasswordCredentialsProvider(effectiveToken, "");
    }

    private void cleanupDirectory(Path directory) {
        try {
            if (Files.exists(directory)) {
                FileSystemUtils.deleteRecursively(directory);
                log.debug("Cleaned up directory: {}", directory);
            }
        } catch (IOException e) {
            log.warn("Failed to cleanup directory: {}", directory, e);
        }
    }

    private void validateRepositorySize(Path repoPath) {
        long maxSizeBytes = appConfig.getMaxRepoSizeMb() * 1024L * 1024L;
        if (maxSizeBytes <= 0) {
            return;
        }

        try (var paths = Files.walk(repoPath)) {
            long size = paths
                    .filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (IOException e) {
                            return 0L;
                        }
                    })
                    .sum();

            if (size > maxSizeBytes) {
                cleanupDirectory(repoPath);
                throw new RuntimeException("Repository size exceeds limit: " +
                        (size / 1024L / 1024L) + "MB > " + appConfig.getMaxRepoSizeMb() + "MB");
            }
        } catch (IOException e) {
            log.warn("Failed to calculate repository size", e);
        }
    }

    private void cleanupOldRepositories(Path tempDir) {
        long retentionMillis = appConfig.getRepoRetentionMinutes() * 60_000L;
        if (retentionMillis <= 0) {
            return;
        }

        try (var paths = Files.list(tempDir)) {
            long now = System.currentTimeMillis();
            paths.filter(Files::isDirectory)
                 .forEach(dir -> {
                     try {
                         long lastModified = Files.getLastModifiedTime(dir).toMillis();
                         if (now - lastModified > retentionMillis) {
                             cleanupDirectory(dir);
                         }
                     } catch (IOException e) {
                         log.debug("Failed to check directory age: {}", dir, e);
                     }
                 });
        } catch (IOException e) {
            log.debug("Failed to cleanup old repositories", e);
        }
    }
}
